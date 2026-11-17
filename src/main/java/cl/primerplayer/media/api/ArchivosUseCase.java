package cl.primerplayer.media.api;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Puerto de entrada: subida y lectura de archivos (imágenes públicas y comprobantes privados). */
public interface ArchivosUseCase {

    long MAX_BYTES = 5L * 1024 * 1024;

    /**
     * Sube una imagen pública (JPEG, PNG o WebP; máx. 5 MB). El tipo se detecta por el contenido, no por la extensión.
     *
     * @throws cl.primerplayer.shared.api.ValidacionException archivo vacío, muy grande o de tipo no admitido
     * @throws AlmacenNoDisponibleException 503 si el almacenamiento no responde
     */
    ArchivoSubido subirImagen(byte[] contenido, long usuarioId);

    /** Sube un comprobante de transferencia (imagen; bucket privado). Mismas reglas que {@link #subirImagen}. */
    ArchivoSubido subirComprobante(byte[] contenido, long usuarioId);

    /**
     * URL firmada de un comprobante, válida por 5 minutos.
     *
     * @throws cl.primerplayer.shared.api.RecursoNoEncontradoException si no existe o no es un comprobante
     */
    UrlTemporal urlComprobante(UUID id);

    /** URL pública de cada imagen (los ids inexistentes o privados no aparecen). */
    Map<UUID, String> urlsPublicas(Collection<UUID> ids);

    /** {@code true} si el archivo existe y es una imagen pública (un comprobante nunca puede mostrarse en la tienda). */
    boolean esImagenPublica(UUID id);

    /** @param url {@code null} para comprobantes (privados) */
    record ArchivoSubido(UUID id, String url, String mime, int bytes, Integer ancho, Integer alto) {}

    record UrlTemporal(String url, Instant expiraEn) {}
}
