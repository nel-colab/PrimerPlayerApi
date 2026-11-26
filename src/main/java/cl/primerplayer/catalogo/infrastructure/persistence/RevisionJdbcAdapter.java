package cl.primerplayer.catalogo.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import cl.primerplayer.catalogo.api.DatosProducto;
import cl.primerplayer.catalogo.api.DatosProducto.ImagenProducto;
import cl.primerplayer.catalogo.api.RevisionesProductoUseCase.EstadoRevision;
import cl.primerplayer.catalogo.api.RevisionesProductoUseCase.TipoRevision;
import cl.primerplayer.catalogo.application.port.out.RevisionRepositorio;
import cl.primerplayer.media.api.ArchivosUseCase;
import cl.primerplayer.shared.api.ConflictoVersionException;
import cl.primerplayer.shared.api.Pagina;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code producto_revision} con JdbcTemplate: los datos propuestos van como JSONB ({@code CAST(? AS jsonb)}). Comparte
 * la transacción JPA (el producto se guarda con {@code saveAndFlush} antes de tocar esta tabla).
 */
@Component
@RequiredArgsConstructor
class RevisionJdbcAdapter implements RevisionRepositorio {

    private static final String COLUMNAS = "id, producto_id, tipo, estado, datos_propuestos::text AS datos_propuestos, "
            + "datos_aprobados::text AS datos_aprobados, version_base, motivo_rechazo, solicitado_por, solicitado_en, "
            + "revisado_por, revisado_en";

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final ArchivosUseCase media;

    @Override
    public long crear(long productoId, TipoRevision tipo, DatosProducto propuesto, int versionBase, long solicitadoPor,
                      Instant ahora) {
        try {
            Long id = jdbc.queryForObject("""
                    INSERT INTO producto_revision (producto_id, tipo, datos_propuestos, version_base, solicitado_por,
                                                   solicitado_en)
                    VALUES (?, ?, CAST(? AS jsonb), ?, ?, ?) RETURNING id
                    """, Long.class, productoId, tipo.name(), aJson(propuesto), versionBase, solicitadoPor, utc(ahora));
            return id;
        } catch (DuplicateKeyException e) {
            throw new ConflictoVersionException();   // uq_producto_revision_pendiente: otra solicitud llegó primero
        }
    }

    @Override
    public Optional<Revision> pendienteDe(long productoId) {
        return uno("SELECT " + COLUMNAS + " FROM producto_revision WHERE producto_id = ? AND estado = 'PENDIENTE'",
                productoId);
    }

    @Override
    public Optional<Revision> bloquear(long id) {
        return uno("SELECT " + COLUMNAS + " FROM producto_revision WHERE id = ? FOR UPDATE", id);
    }

    @Override
    public void reemplazarPropuesta(long id, DatosProducto propuesto, int versionBase, long solicitadoPor,
                                    Instant ahora) {
        jdbc.update("""
                UPDATE producto_revision
                SET datos_propuestos = CAST(? AS jsonb), version_base = ?, solicitado_por = ?, solicitado_en = ?
                WHERE id = ? AND estado = 'PENDIENTE'
                """, aJson(propuesto), versionBase, solicitadoPor, utc(ahora), id);
    }

    @Override
    public void resolver(long id, EstadoRevision estado, String motivoRechazo, Long revisadoPor, Instant ahora) {
        jdbc.update("""
                UPDATE producto_revision SET estado = ?, motivo_rechazo = ?, revisado_por = ?, revisado_en = ?
                WHERE id = ? AND estado = 'PENDIENTE'
                """, estado.name(), motivoRechazo, revisadoPor, utc(ahora), id);
    }

    @Override
    public void guardarAprobados(long id, DatosProducto aprobados) {
        jdbc.update("UPDATE producto_revision SET datos_aprobados = CAST(? AS jsonb) WHERE id = ? AND estado = 'APROBADA'",
                aJson(aprobados), id);
    }

    // S2077: el SQL solo concatena constantes y fragmentos fijos; los valores van siempre como parámetros "?".
    @SuppressWarnings("java:S2077")
    @Override
    public Pagina<Revision> buscar(EstadoRevision estado, int page, int size) {
        String where = estado == null ? "" : " WHERE estado = ?";
        List<Object> args = new ArrayList<>();
        if (estado != null) {
            args.add(estado.name());
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM producto_revision" + where, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        List<Revision> filas = jdbc.query("SELECT " + COLUMNAS + " FROM producto_revision" + where
                + " ORDER BY solicitado_en, id LIMIT ? OFFSET ?", this::fila, args.toArray());
        return Pagina.de(filas, page, size, total == null ? 0 : total);
    }

    @Override
    public List<Revision> deProducto(long productoId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM producto_revision WHERE producto_id = ?"
                + " ORDER BY solicitado_en DESC, id DESC", this::fila, productoId);
    }

    // ---------- mapeo ----------

    private Optional<Revision> uno(String sql, Object arg) {
        return jdbc.query(sql, this::fila, arg).stream().findFirst();
    }

    private Revision fila(ResultSet rs, int n) throws SQLException {
        return new Revision(rs.getLong("id"), rs.getLong("producto_id"), TipoRevision.valueOf(rs.getString("tipo")),
                EstadoRevision.valueOf(rs.getString("estado")), deJson(rs.getString("datos_propuestos")),
                deJson(rs.getString("datos_aprobados")), rs.getInt("version_base"), rs.getString("motivo_rechazo"), rs.getLong("solicitado_por"),
                instante(rs, "solicitado_en"), rs.getObject("revisado_por", Long.class),
                instante(rs, "revisado_en"));
    }

    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime t = rs.getObject(columna, OffsetDateTime.class);
        return t == null ? null : t.toInstant();
    }

    private static OffsetDateTime utc(Instant t) {
        return t.atOffset(ZoneOffset.UTC);
    }

    // Formato propio en el JSON (no el record del api): renombrar un campo del api no rompe propuestas ya guardadas.

    private String aJson(DatosProducto d) {
        if (d == null) {
            return null;
        }
        List<ImagenJson> imagenes = d.imagenes().stream()
                .map(i -> new ImagenJson(i.archivoId() == null ? i.url() : null, i.portada(), i.archivoId()))
                .toList();
        return json.writeValueAsString(new PropuestaJson(d.nombre(), d.descripcion(), d.precio(), d.categoria(),
                d.tipo(), d.idioma(), d.marca(), d.plataforma(), d.franquicia(), d.linea(), d.codigoBarras(),
                d.limitePorCliente(), d.publicado(), imagenes));
    }

    private DatosProducto deJson(String texto) {
        if (texto == null) {
            return null;
        }
        PropuestaJson p = json.readValue(texto, PropuestaJson.class);
        List<ImagenJson> guardadas = p.imagenes() == null ? List.of() : p.imagenes();
        Map<UUID, String> urls = media.urlsPublicas(
                guardadas.stream().map(ImagenJson::archivoId).filter(Objects::nonNull).distinct().toList());
        List<ImagenProducto> imagenes = guardadas.stream()
                .map(i -> new ImagenProducto(i.archivoId() == null ? i.url() : urls.get(i.archivoId()), i.portada(),
                        i.archivoId()))
                .toList();
        return new DatosProducto(p.nombre(), p.descripcion(), p.precio(), p.categoria(), p.tipo(), p.idioma(),
                p.marca(), p.plataforma(), p.franquicia(), p.linea(), p.codigoBarras(), p.limitePorCliente(),
                p.publicado(), imagenes);
    }

    record PropuestaJson(String nombre, String descripcion, long precio, String categoria, String tipo, String idioma,
                         String marca, String plataforma, String franquicia, String linea, String codigoBarras,
                         Integer limitePorCliente, boolean publicado, List<ImagenJson> imagenes) {
    }

    /** Una imagen subida guarda solo su {@code archivoId}: la URL se resuelve al leerla. */
    record ImagenJson(String url, boolean portada, UUID archivoId) {
    }
}
