package cl.primerplayer.auditoria.infrastructure;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import cl.primerplayer.auditoria.api.ConsultarAuditoriaUseCase.Registro;
import lombok.RequiredArgsConstructor;

/** Tabla {@code auditoria}: lectura con filtros y escritura (la usan los escuchas de eventos). */
@Component
@RequiredArgsConstructor
public class AuditoriaRepositorio {

    private final JdbcTemplate jdbc;

    public record Filtro(String entidad, String entidadId, Instant desde, Instant hasta) {}

    public List<Registro> buscar(Filtro f, int page, int size) {
        List<Object> args = new ArrayList<>();
        String where = where(f, args);
        args.add(size);
        args.add((long) page * size);
        return jdbc.query("SELECT id, ocurrido_en, usuario_id, accion, entidad, entidad_id, antes::text AS antes, "
                + "despues::text AS despues, host(ip) AS ip FROM auditoria" + where
                + " ORDER BY ocurrido_en DESC, id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new Registro(rs.getLong("id"), rs.getObject("ocurrido_en", OffsetDateTime.class).toInstant(),
                        (Long) rs.getObject("usuario_id"), null, rs.getString("accion"), rs.getString("entidad"),
                        rs.getString("entidad_id"), rs.getString("antes"), rs.getString("despues"), rs.getString("ip")),
                args.toArray());
    }

    public long contar(Filtro f) {
        List<Object> args = new ArrayList<>();
        Long n = jdbc.queryForObject("SELECT count(*) FROM auditoria" + where(f, args), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    /** @param antes / despues JSON (o {@code null}) */
    public void registrar(Instant ocurridoEn, Long usuarioId, String accion, String entidad, String entidadId,
                          String antes, String despues) {
        jdbc.update("""
                INSERT INTO auditoria (ocurrido_en, usuario_id, accion, entidad, entidad_id, antes, despues)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))
                """, ocurridoEn.atOffset(ZoneOffset.UTC), usuarioId, accion, entidad, entidadId, antes, despues);
    }

    private static String where(Filtro f, List<Object> args) {
        List<String> c = new ArrayList<>();
        if (f.entidad() != null) {
            c.add("entidad = ?");
            args.add(f.entidad());
        }
        if (f.entidadId() != null) {
            c.add("entidad_id = ?");
            args.add(f.entidadId());
        }
        if (f.desde() != null) {
            c.add("ocurrido_en >= ?");
            args.add(f.desde().atOffset(ZoneOffset.UTC));
        }
        if (f.hasta() != null) {
            c.add("ocurrido_en < ?");
            args.add(f.hasta().atOffset(ZoneOffset.UTC));
        }
        return c.isEmpty() ? "" : " WHERE " + String.join(" AND ", c);
    }
}
