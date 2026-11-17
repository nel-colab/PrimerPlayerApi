package cl.primerplayer.identidad.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import cl.primerplayer.identidad.api.AdminUsuariosUseCase.Baneo;
import cl.primerplayer.identidad.api.AdminUsuariosUseCase.Estado;
import cl.primerplayer.identidad.api.AdminUsuariosUseCase.Filtro;
import cl.primerplayer.identidad.api.AdminUsuariosUseCase.Indicadores;
import cl.primerplayer.identidad.api.AdminUsuariosUseCase.ListaNegra;
import cl.primerplayer.identidad.api.AdminUsuariosUseCase.UsuarioAdmin;
import cl.primerplayer.identidad.application.port.out.AdminUsuariosRepositorio;
import cl.primerplayer.shared.api.Rol;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class AdminUsuariosJdbcAdapter implements AdminUsuariosRepositorio {

    /** Visible en el panel: ni el cliente anónimo de mostrador ni los eliminados. */
    private static final String BASE = " FROM usuario u WHERE NOT u.es_anonimo AND u.eliminado_en IS NULL";
    /** Baneo vigente al instante ? (el primer parámetro). */
    private static final String BANEADO = "(u.baneo_motivo IS NOT NULL AND (u.baneo_hasta IS NULL OR u.baneo_hasta > ?))";
    private static final long LOCK_ADMINS = 0x5050_4144_4D49_4E53L;   // "PPADMINS"

    private final JdbcTemplate jdbc;

    @Override
    public List<UsuarioAdmin> buscar(Filtro f, Instant ahora) {
        List<Object> args = new ArrayList<>();
        args.add(utc(ahora));
        String where = where(f, ahora, args);
        args.add(f.size());
        args.add((long) f.page() * f.size());
        return jdbc.query("SELECT u.*, " + BANEADO + " AS baneado" + BASE + where
                + " ORDER BY u.creado_en DESC, u.id DESC LIMIT ? OFFSET ?", (rs, n) -> fila(rs), args.toArray());
    }

    @Override
    public long contar(Filtro f, Instant ahora) {
        List<Object> args = new ArrayList<>();
        Long n = jdbc.queryForObject("SELECT count(*)" + BASE + where(f, ahora, args), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    @Override
    public Indicadores indicadores(Instant ahora) {
        return jdbc.queryForObject("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE u.rol = 'CLIENTE') AS clientes,
                       count(*) FILTER (WHERE u.rol <> 'CLIENTE') AS internos,
                       count(*) FILTER (WHERE %s) AS baneados,
                       count(*) FILTER (WHERE u.lista_negra_motivo IS NOT NULL) AS lista_negra,
                       count(*) FILTER (WHERE u.email_verificado_en IS NULL) AS sin_verificar
                """.formatted(BANEADO) + BASE, (rs, n) -> new Indicadores(rs.getLong("total"), rs.getLong("clientes"),
                        rs.getLong("internos"), rs.getLong("baneados"), rs.getLong("lista_negra"), rs.getLong("sin_verificar")),
                utc(ahora));
    }

    @Override
    public Optional<UsuarioAdmin> bloquear(long usuarioId, Instant ahora) {
        return jdbc.query("SELECT u.*, " + BANEADO + " AS baneado" + BASE + " AND u.id = ? FOR UPDATE",
                (rs, n) -> fila(rs), utc(ahora), usuarioId).stream().findFirst();
    }

    @Override
    public long adminsActivos(Instant ahora) {
        Long n = jdbc.queryForObject("SELECT count(*)" + BASE + " AND u.rol = 'ADMIN' AND u.activo AND NOT " + BANEADO,
                Long.class, utc(ahora));
        return n == null ? 0 : n;
    }

    @Override
    public void bloquearAdmins() {
        jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> null, LOCK_ADMINS);
    }

    @Override
    public void cambiarRol(long id, Rol rol, Instant ahora) {
        jdbc.update("UPDATE usuario SET rol = ?, actualizado_en = ? WHERE id = ?", rol.name(), utc(ahora), id);
    }

    @Override
    public void banear(long id, String motivo, Instant hasta, Instant ahora) {
        jdbc.update("UPDATE usuario SET baneo_motivo = ?, baneo_hasta = ?, baneo_creado_en = ?, actualizado_en = ? WHERE id = ?",
                motivo, hasta == null ? null : utc(hasta), utc(ahora), utc(ahora), id);
    }

    @Override
    public void quitarBaneo(long id, Instant ahora) {
        jdbc.update("UPDATE usuario SET baneo_motivo = NULL, baneo_hasta = NULL, baneo_creado_en = NULL, actualizado_en = ? WHERE id = ?",
                utc(ahora), id);
    }

    @Override
    public void ponerEnListaNegra(long id, String motivo, Instant ahora) {
        jdbc.update("UPDATE usuario SET lista_negra_motivo = ?, lista_negra_en = ?, actualizado_en = ? WHERE id = ?",
                motivo, utc(ahora), utc(ahora), id);
    }

    @Override
    public void quitarDeListaNegra(long id, Instant ahora) {
        jdbc.update("UPDATE usuario SET lista_negra_motivo = NULL, lista_negra_en = NULL, actualizado_en = ? WHERE id = ?",
                utc(ahora), id);
    }

    @Override
    public void marcarCambioPassword(long id, Instant ahora) {
        jdbc.update("UPDATE usuario SET cambio_password_pendiente = TRUE, actualizado_en = ? WHERE id = ?", utc(ahora), id);
    }

    // ---------- filtros y mapeo ----------

    private static String where(Filtro f, Instant ahora, List<Object> args) {
        StringBuilder w = new StringBuilder();
        if (f.q() != null && !f.q().isBlank()) {
            String q = f.q().strip();
            w.append(" AND (f_unaccent(lower(u.nombre)) LIKE f_unaccent(lower(?)) OR u.email::text ILIKE ?");
            args.add("%" + escapar(q) + "%");
            args.add("%" + escapar(q) + "%");
            if (q.matches("\\d{1,18}")) {
                w.append(" OR u.id = ?");
                args.add(Long.parseLong(q));
            }
            w.append(")");
        }
        if (!f.roles().isEmpty()) {
            w.append(" AND u.rol IN (").append(String.join(",", f.roles().stream().map(r -> "?").toList())).append(")");
            f.roles().forEach(r -> args.add(r.name()));
        }
        if (!f.estados().isEmpty()) {
            List<String> o = new ArrayList<>();
            for (Estado e : f.estados()) {
                switch (e) {
                    case ACTIVO -> {
                        o.add("(u.activo AND NOT " + BANEADO + ")");
                        args.add(utc(ahora));
                    }
                    case BANEADO -> {
                        o.add(BANEADO);
                        args.add(utc(ahora));
                    }
                    case LISTA_NEGRA -> o.add("u.lista_negra_motivo IS NOT NULL");
                    case SIN_VERIFICAR -> o.add("u.email_verificado_en IS NULL");
                }
            }
            w.append(" AND (").append(String.join(" OR ", o)).append(")");
        }
        return w.toString();
    }

    private static String escapar(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static UsuarioAdmin fila(ResultSet rs) throws SQLException {
        Baneo baneo = rs.getBoolean("baneado")
                ? new Baneo(rs.getString("baneo_motivo"), instante(rs, "baneo_hasta"), instante(rs, "baneo_creado_en"))
                : null;
        ListaNegra lista = rs.getString("lista_negra_motivo") == null ? null
                : new ListaNegra(rs.getString("lista_negra_motivo"), instante(rs, "lista_negra_en"));
        return new UsuarioAdmin(rs.getLong("id"), rs.getString("email"), rs.getString("nombre"),
                Rol.valueOf(rs.getString("rol")), rs.getString("telefono"), rs.getObject("email_verificado_en") != null,
                baneo, lista, rs.getBoolean("cambio_password_pendiente"), instante(rs, "creado_en"),
                instante(rs, "ultimo_acceso_en"));
    }

    private static Instant instante(ResultSet rs, String col) throws SQLException {
        OffsetDateTime o = rs.getObject(col, OffsetDateTime.class);
        return o == null ? null : o.toInstant();
    }

    private static OffsetDateTime utc(Instant i) {
        return i.atOffset(ZoneOffset.UTC);
    }
}
