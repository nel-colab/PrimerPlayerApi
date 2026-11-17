package cl.primerplayer.eventos.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import cl.primerplayer.eventos.api.TipoEvento;
import cl.primerplayer.eventos.application.port.out.AgendaRepositorio;
import cl.primerplayer.eventos.application.port.out.CuposRepositorio;
import cl.primerplayer.eventos.domain.Bloque;
import lombok.RequiredArgsConstructor;

/**
 * Adaptadores JDBC: SQL que no encaja en JPA (advisory lock, rangos tstzrange y UPDATE ... RETURNING).
 * JdbcTemplate participa de la misma transacción que JPA.
 */
final class AgendaYCuposJdbcAdapter {
    private AgendaYCuposJdbcAdapter() {}

    static OffsetDateTime utc(Instant i) {
        return i.atOffset(ZoneOffset.UTC);
    }

    static Instant instante(ResultSet rs, String col) throws SQLException {
        OffsetDateTime o = rs.getObject(col, OffsetDateTime.class);
        return o == null ? null : o.toInstant();
    }
}

@Component
@RequiredArgsConstructor
class AgendaJdbcAdapter implements AgendaRepositorio {

    /** Clave arbitraria y fija del advisory lock de la agenda. */
    static final long CLAVE_LOCK_AGENDA = 0x5050_4147_454E_4441L;   // "PPAGENDA"

    private final JdbcTemplate jdbc;

    @Override
    public void bloquearAgenda() {
        jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> null, CLAVE_LOCK_AGENDA);
    }

    @Override
    public List<Ocupacion> traslapes(Instant desde, Instant hasta, Duration duracionEstimada, Long excluirEventoId) {
        return jdbc.query("""
                SELECT id, tipo, publicado, fecha_hora AS inicio,
                       COALESCE(fecha_hora_fin, fecha_hora + make_interval(secs => ?)) AS fin
                FROM evento
                WHERE eliminado_en IS NULL
                  AND id <> ?
                  AND tstzrange(fecha_hora, COALESCE(fecha_hora_fin, fecha_hora + make_interval(secs => ?)))
                      && tstzrange(?, ?)
                ORDER BY fecha_hora
                """,
                (rs, n) -> new Ocupacion(rs.getLong("id"), TipoEvento.desdeSlug(rs.getString("tipo")),
                        new Bloque(AgendaYCuposJdbcAdapter.instante(rs, "inicio"), AgendaYCuposJdbcAdapter.instante(rs, "fin")),
                        rs.getBoolean("publicado")),
                (double) duracionEstimada.toSeconds(), excluirEventoId == null ? -1L : excluirEventoId,   // -1: no excluye nada
                (double) duracionEstimada.toSeconds(), AgendaYCuposJdbcAdapter.utc(desde), AgendaYCuposJdbcAdapter.utc(hasta));
    }
}

@Component
@RequiredArgsConstructor
class CuposJdbcAdapter implements CuposRepositorio {

    private final JdbcTemplate jdbc;

    @Override
    public Optional<Integer> ocupar(long eventoId, int cantidad, Instant ahora) {
        return jdbc.query("""
                UPDATE evento SET cupos_ocupados = cupos_ocupados + ?
                WHERE id = ? AND publicado AND eliminado_en IS NULL AND tipo <> 'cumpleanos'
                  AND fecha_hora > ? AND cupos_ocupados + ? <= cupos_totales
                RETURNING cupos_totales - cupos_ocupados AS disponibles
                """, (rs, n) -> rs.getInt("disponibles"),
                cantidad, eventoId, AgendaYCuposJdbcAdapter.utc(ahora), cantidad).stream().findFirst();
    }

    @Override
    public Optional<Integer> liberar(long eventoId, int cantidad) {
        return jdbc.query("""
                UPDATE evento SET cupos_ocupados = cupos_ocupados - ?
                WHERE id = ? AND cupos_ocupados >= ?
                RETURNING cupos_totales - cupos_ocupados AS disponibles
                """, (rs, n) -> rs.getInt("disponibles"), cantidad, eventoId, cantidad).stream().findFirst();
    }

    @Override
    public Optional<EstadoCupos> estado(long eventoId) {
        return jdbc.query("""
                SELECT (publicado AND eliminado_en IS NULL AND tipo <> 'cumpleanos') AS vendible, fecha_hora,
                       COALESCE(cupos_totales - cupos_ocupados, 0) AS disponibles
                FROM evento WHERE id = ?
                """, (rs, n) -> new EstadoCupos(rs.getBoolean("vendible"),
                        AgendaYCuposJdbcAdapter.instante(rs, "fecha_hora"), rs.getInt("disponibles")), eventoId)
                .stream().findFirst();
    }
}
