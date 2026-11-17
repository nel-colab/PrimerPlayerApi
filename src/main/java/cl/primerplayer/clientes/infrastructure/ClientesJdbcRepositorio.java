package cl.primerplayer.clientes.infrastructure;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import cl.primerplayer.clientes.api.CuentaClienteUseCase.DatosDireccion;
import cl.primerplayer.clientes.api.CuentaClienteUseCase.Direccion;
import lombok.RequiredArgsConstructor;

/** Tablas {@code direccion}, {@code favorito} y {@code alerta_stock}. Todo filtrado por usuario. */
@Component
@RequiredArgsConstructor
public class ClientesJdbcRepositorio {

    private final JdbcTemplate jdbc;

    // ---------- direcciones ----------

    public List<Direccion> direcciones(long usuarioId) {
        return jdbc.query("SELECT * FROM direccion WHERE usuario_id = ? ORDER BY principal DESC, id",
                (rs, n) -> new Direccion(rs.getLong("id"), rs.getString("alias"), rs.getString("calle"),
                        rs.getString("comuna"), rs.getString("region"), rs.getString("referencia"),
                        rs.getBoolean("principal")), usuarioId);
    }

    public Optional<Direccion> direccion(long usuarioId, long id) {
        return direcciones(usuarioId).stream().filter(d -> d.id() == id).findFirst();
    }

    public int contarDirecciones(long usuarioId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM direccion WHERE usuario_id = ?", Integer.class, usuarioId);
        return n == null ? 0 : n;
    }

    /** Antes de marcar otra como principal (índice único parcial: una principal por usuario). */
    public void desmarcarPrincipal(long usuarioId) {
        jdbc.update("UPDATE direccion SET principal = FALSE WHERE usuario_id = ? AND principal", usuarioId);
    }

    public long insertarDireccion(long usuarioId, DatosDireccion d) {
        return jdbc.queryForObject("""
                INSERT INTO direccion (usuario_id, alias, calle, comuna, region, referencia, principal)
                VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, usuarioId, d.alias(), d.calle(), d.comuna(), d.region(), d.referencia(), d.principal());
    }

    public void actualizarDireccion(long usuarioId, long id, DatosDireccion d) {
        jdbc.update("""
                UPDATE direccion SET alias = ?, calle = ?, comuna = ?, region = ?, referencia = ?, principal = ?
                WHERE id = ? AND usuario_id = ?
                """, d.alias(), d.calle(), d.comuna(), d.region(), d.referencia(), d.principal(), id, usuarioId);
    }

    public boolean eliminarDireccion(long usuarioId, long id) {
        return jdbc.update("DELETE FROM direccion WHERE id = ? AND usuario_id = ?", id, usuarioId) == 1;
    }

    /** Si no queda ninguna principal, la más antigua pasa a serlo. */
    public void asegurarPrincipal(long usuarioId) {
        jdbc.update("""
                UPDATE direccion SET principal = TRUE
                WHERE id = (SELECT min(id) FROM direccion WHERE usuario_id = ?)
                  AND NOT EXISTS (SELECT 1 FROM direccion WHERE usuario_id = ? AND principal)
                """, usuarioId, usuarioId);
    }

    // ---------- favoritos y alertas ----------

    public record Marca(long productoId, Instant creadaEn, Instant notificadaEn) {}

    public List<Marca> favoritos(long usuarioId) {
        return jdbc.query("SELECT producto_id, creado_en FROM favorito WHERE usuario_id = ? ORDER BY creado_en DESC",
                (rs, n) -> new Marca(rs.getLong("producto_id"), rs.getObject("creado_en", OffsetDateTime.class).toInstant(), null),
                usuarioId);
    }

    public void agregarFavorito(long usuarioId, long productoId, Instant ahora) {
        jdbc.update("INSERT INTO favorito (usuario_id, producto_id, creado_en) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                usuarioId, productoId, utc(ahora));
    }

    public void quitarFavorito(long usuarioId, long productoId) {
        jdbc.update("DELETE FROM favorito WHERE usuario_id = ? AND producto_id = ?", usuarioId, productoId);
    }

    public List<Marca> alertas(long usuarioId) {
        return jdbc.query("""
                SELECT producto_id, creado_en, notificado_en FROM alerta_stock WHERE usuario_id = ?
                ORDER BY (notificado_en IS NULL) DESC, creado_en DESC
                """, (rs, n) -> new Marca(rs.getLong("producto_id"), rs.getObject("creado_en", OffsetDateTime.class).toInstant(),
                        Optional.ofNullable(rs.getObject("notificado_en", OffsetDateTime.class)).map(OffsetDateTime::toInstant)
                                .orElse(null)), usuarioId);
    }

    /** Crea la alerta o la rearma si ya se había notificado. */
    public void crearAlerta(long usuarioId, long productoId, Instant ahora) {
        jdbc.update("""
                INSERT INTO alerta_stock (usuario_id, producto_id, creado_en) VALUES (?, ?, ?)
                ON CONFLICT (usuario_id, producto_id) DO UPDATE SET notificado_en = NULL, creado_en = EXCLUDED.creado_en
                """, usuarioId, productoId, utc(ahora));
    }

    public void quitarAlerta(long usuarioId, long productoId) {
        jdbc.update("DELETE FROM alerta_stock WHERE usuario_id = ? AND producto_id = ?", usuarioId, productoId);
    }

    /** Marca como notificadas las alertas pendientes del producto (atómico). @return usuarios a avisar */
    public List<Long> marcarNotificadas(long productoId, Instant ahora) {
        return jdbc.queryForList("""
                UPDATE alerta_stock SET notificado_en = ? WHERE producto_id = ? AND notificado_en IS NULL
                RETURNING usuario_id
                """, Long.class, utc(ahora), productoId);
    }

    private static OffsetDateTime utc(Instant i) {
        return i.atOffset(ZoneOffset.UTC);
    }
}
