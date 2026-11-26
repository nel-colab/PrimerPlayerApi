package cl.primerplayer.catalogo.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import cl.primerplayer.catalogo.application.port.out.MaestrosAdminRepositorio;
import lombok.RequiredArgsConstructor;

/**
 * Categorías y franquicias con JdbcTemplate (las entidades JPA de estas tablas son de solo lectura). El uso se cuenta
 * en la misma consulta: productos que las referencian (también los eliminados) y revisiones que las mencionan en sus
 * datos propuestos o aprobados (JSON, por slug o por nombre). La agrupadora {@code todos} nunca se toca.
 */
@Component
@RequiredArgsConstructor
class MaestrosAdminJdbcAdapter implements MaestrosAdminRepositorio {

    private static final String SELECT_CATEGORIA = """
            SELECT c.id, c.slug, c.titulo, c.orden,
                   (SELECT count(*) FROM producto p WHERE p.categoria_id = c.id) AS productos,
                   (SELECT count(*) FROM producto_revision r
                     WHERE r.datos_propuestos ->> 'categoria' = c.slug
                        OR r.datos_aprobados ->> 'categoria' = c.slug) AS revisiones
            FROM categoria c
            WHERE c.slug <> 'todos'
            """;

    private static final String SELECT_FRANQUICIA = """
            SELECT f.id, f.nombre,
                   (SELECT count(*) FROM producto p WHERE p.franquicia_id = f.id) AS productos,
                   (SELECT count(*) FROM producto_revision r
                     WHERE lower(r.datos_propuestos ->> 'franquicia') = lower(f.nombre)
                        OR lower(r.datos_aprobados ->> 'franquicia') = lower(f.nombre)) AS revisiones
            FROM franquicia f
            """;

    private final JdbcTemplate jdbc;

    // ---------- categorías ----------

    @Override
    public List<FilaCategoria> categorias() {
        return jdbc.query(SELECT_CATEGORIA + " ORDER BY c.orden, c.id", (rs, n) -> categoria(rs));
    }

    @Override
    public Optional<FilaCategoria> categoria(int id) {
        return jdbc.query(SELECT_CATEGORIA + " AND c.id = ?", (rs, n) -> categoria(rs), id).stream().findFirst();
    }

    @Override
    public boolean existeCategoria(String slug, String titulo, Integer exceptoId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM categoria
                               WHERE (slug = ? OR lower(titulo) = lower(?))
                                 AND (CAST(? AS integer) IS NULL OR id <> CAST(? AS integer)))
                """, Boolean.class, slug, titulo, exceptoId, exceptoId));
    }

    @Override
    public int siguienteOrden() {
        Integer max = jdbc.queryForObject("SELECT max(orden) FROM categoria", Integer.class);
        return max == null ? 10 : Math.min(max + 10, 9999);
    }

    @Override
    public int crearCategoria(String slug, String titulo, int orden) {
        Integer id = jdbc.queryForObject("""
                INSERT INTO categoria (slug, titulo, parent_id, es_hoja, orden)
                VALUES (?, ?, NULL, TRUE, ?) RETURNING id
                """, Integer.class, slug, titulo, orden);
        return id == null ? 0 : id;
    }

    @Override
    public void actualizarCategoria(int id, String titulo, int orden) {
        jdbc.update("UPDATE categoria SET titulo = ?, orden = ? WHERE id = ? AND slug <> 'todos'", titulo, orden, id);
    }

    @Override
    public void eliminarCategoria(int id) {
        jdbc.update("DELETE FROM categoria WHERE id = ? AND slug <> 'todos'", id);
    }

    private static FilaCategoria categoria(ResultSet rs) throws SQLException {
        return new FilaCategoria(rs.getInt("id"), rs.getString("slug"), rs.getString("titulo"), rs.getInt("orden"),
                rs.getLong("productos"), rs.getLong("revisiones"));
    }

    // ---------- franquicias ----------

    @Override
    public List<FilaFranquicia> franquicias() {
        return jdbc.query(SELECT_FRANQUICIA + " ORDER BY lower(f.nombre), f.id", (rs, n) -> franquicia(rs));
    }

    @Override
    public Optional<FilaFranquicia> franquicia(int id) {
        return jdbc.query(SELECT_FRANQUICIA + " WHERE f.id = ?", (rs, n) -> franquicia(rs), id).stream().findFirst();
    }

    @Override
    public boolean existeFranquicia(String nombre, Integer exceptoId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM franquicia
                               WHERE lower(nombre) = lower(?)
                                 AND (CAST(? AS integer) IS NULL OR id <> CAST(? AS integer)))
                """, Boolean.class, nombre, exceptoId, exceptoId));
    }

    @Override
    public int crearFranquicia(String nombre) {
        Integer id = jdbc.queryForObject("INSERT INTO franquicia (nombre) VALUES (?) RETURNING id", Integer.class, nombre);
        return id == null ? 0 : id;
    }

    @Override
    public void renombrarFranquicia(int id, String nombre) {
        jdbc.update("UPDATE franquicia SET nombre = ? WHERE id = ?", nombre, id);
    }

    @Override
    public int renombrarFranquiciaEnRevisionesPendientes(String anterior, String nuevo) {
        return jdbc.update("""
                UPDATE producto_revision
                SET datos_propuestos = jsonb_set(datos_propuestos, '{franquicia}', to_jsonb(CAST(? AS text)))
                WHERE estado = 'PENDIENTE' AND lower(datos_propuestos ->> 'franquicia') = lower(?)
                """, nuevo, anterior);
    }

    @Override
    public void eliminarFranquicia(int id) {
        jdbc.update("DELETE FROM franquicia WHERE id = ?", id);
    }

    private static FilaFranquicia franquicia(ResultSet rs) throws SQLException {
        return new FilaFranquicia(rs.getInt("id"), rs.getString("nombre"), rs.getLong("productos"),
                rs.getLong("revisiones"));
    }
}
