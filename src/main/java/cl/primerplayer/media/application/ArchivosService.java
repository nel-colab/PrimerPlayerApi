package cl.primerplayer.media.application;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import cl.primerplayer.media.api.ArchivosUseCase;
import cl.primerplayer.media.application.port.out.AlmacenObjetos;
import cl.primerplayer.media.application.port.out.ArchivoRepositorio;
import cl.primerplayer.media.application.port.out.ArchivoRepositorio.Fila;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import cl.primerplayer.shared.api.ValidacionException;
import lombok.RequiredArgsConstructor;

/**
 * Sin {@code @Transactional} a propósito: primero se sube a S3 (HTTP) y recién después se registra la fila (un
 * INSERT autocommit). Si el INSERT fallara queda un objeto huérfano en S3, que es inofensivo.
 */
@Service
@RequiredArgsConstructor
class ArchivosService implements ArchivosUseCase {

    static final String BUCKET_PUBLICO = "public-media";
    static final String BUCKET_PRIVADO = "private-docs";
    static final Duration VIGENCIA_FIRMA = Duration.ofMinutes(5);
    private static final DateTimeFormatter CARPETA = DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneOffset.UTC);

    private final AlmacenObjetos almacen;
    private final ArchivoRepositorio archivos;
    private final Clock clock;

    @Override
    public ArchivoSubido subirImagen(byte[] contenido, long usuarioId) {
        Fila f = subir(contenido, usuarioId, BUCKET_PUBLICO, "imagenes");
        return new ArchivoSubido(f.id(), almacen.urlPublica(f.bucket(), f.clave()), f.mime(), f.bytes(), f.ancho(), f.alto());
    }

    @Override
    public ArchivoSubido subirComprobante(byte[] contenido, long usuarioId) {
        Fila f = subir(contenido, usuarioId, BUCKET_PRIVADO, "comprobantes");
        return new ArchivoSubido(f.id(), null, f.mime(), f.bytes(), f.ancho(), f.alto());
    }

    @Override
    public UrlTemporal urlComprobante(UUID id) {
        Fila f = archivos.porId(id).filter(x -> BUCKET_PRIVADO.equals(x.bucket()))
                .orElseThrow(() -> new RecursoNoEncontradoException("El comprobante no existe."));
        return new UrlTemporal(almacen.urlFirmada(f.bucket(), f.clave(), VIGENCIA_FIRMA), clock.instant().plus(VIGENCIA_FIRMA));
    }

    @Override
    public Map<UUID, String> urlsPublicas(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return archivos.porIds(ids).stream().filter(f -> BUCKET_PUBLICO.equals(f.bucket()))
                .collect(Collectors.toUnmodifiableMap(Fila::id, f -> almacen.urlPublica(f.bucket(), f.clave())));
    }

    @Override
    public boolean esImagenPublica(UUID id) {
        return archivos.porId(id).map(f -> BUCKET_PUBLICO.equals(f.bucket())).orElse(false);
    }

    // ---------- subida ----------

    private Fila subir(byte[] contenido, long usuarioId, String bucket, String carpeta) {
        if (contenido == null || contenido.length == 0) {
            throw new ValidacionException("archivo", "El archivo está vacío.");
        }
        if (contenido.length > MAX_BYTES) {
            throw new ValidacionException("archivo", "El archivo supera los 5 MB.");
        }
        Tipo tipo = Tipo.detectar(contenido);
        if (tipo == null) {
            throw new ValidacionException("archivo", "Formato no admitido: usa JPEG, PNG o WebP.");
        }
        int[] dims = dimensiones(contenido, tipo);
        UUID id = UUID.randomUUID();
        Instant ahora = clock.instant();
        String clave = carpeta + "/" + CARPETA.format(ahora) + "/" + id + "." + tipo.extension;

        almacen.guardar(bucket, clave, contenido, tipo.mime);   // HTTP: fuera de toda transacción
        Fila f = new Fila(id, bucket, clave, tipo.mime, contenido.length, dims == null ? null : dims[0],
                dims == null ? null : dims[1], sha256(contenido), usuarioId, ahora);
        archivos.insertar(f);
        return f;
    }

    /** Ancho y alto para JPEG/PNG; WebP no lo lee ImageIO estándar (queda null). */
    private static int[] dimensiones(byte[] contenido, Tipo tipo) {
        if (tipo == Tipo.WEBP) {
            return null;
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(contenido));
            if (img == null) {
                throw new ValidacionException("archivo", "La imagen está dañada o no se puede leer.");
            }
            return new int[] {img.getWidth(), img.getHeight()};
        } catch (IOException e) {
            throw new ValidacionException("archivo", "La imagen está dañada o no se puede leer.");
        }
    }

    private static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Tipos admitidos (los mismos del CHECK de {@code archivo.mime}), detectados por su firma de bytes. */
    enum Tipo {
        JPEG("image/jpeg", "jpg"), PNG("image/png", "png"), WEBP("image/webp", "webp");

        final String mime;
        final String extension;

        Tipo(String mime, String extension) {
            this.mime = mime;
            this.extension = extension;
        }

        static Tipo detectar(byte[] b) {
            if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
                return JPEG;
            }
            if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
                return PNG;
            }
            if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
                return WEBP;
            }
            return null;
        }
    }
}
