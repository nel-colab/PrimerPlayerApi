package cl.primerplayer.eventos.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import cl.primerplayer.eventos.api.ConsultarInscripcionesUseCase.EstadoInscripcion;
import cl.primerplayer.eventos.api.ConsultarInscripcionesUseCase.InscripcionVista;
import cl.primerplayer.eventos.api.TipoEvento;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import cl.primerplayer.eventos.application.port.out.InscripcionRepositorio;
import lombok.RequiredArgsConstructor;

/** Adaptador JDBC de {@code inscripcion}: el snapshot del evento se copia en el mismo INSERT ... SELECT. */
@Component
@RequiredArgsConstructor
class InscripcionJdbcAdapter implements InscripcionRepositorio {

    private final JdbcTemplate jdbc;

    @Override
    public void insertarReservada(long eventoId, long clienteId, long pedidoId, int cantidad, Instant ahora) {
        int filas = jdbc.update("""
                INSERT INTO inscripcion (evento_id, cliente_id, pedido_id, cantidad, estado, titulo_snapshot,
                                         fecha_snapshot, lugar_snapshot, precio_snapshot, creado_en)
                SELECT id, ?, ?, ?, 'RESERVADA', titulo, fecha_hora, lugar, precio, ?
                FROM evento WHERE id = ?
                """, clienteId, pedidoId, cantidad, AgendaYCuposJdbcAdapter.utc(ahora), eventoId);
        if (filas != 1) {
            throw new IllegalStateException("No existe el evento " + eventoId);
        }
    }

    @Override
    public int confirmarDelPedido(long pedidoId) {
        return jdbc.update("UPDATE inscripcion SET estado = 'CONFIRMADA' WHERE pedido_id = ? AND estado = 'RESERVADA'",
                pedidoId);
    }

    @Override
    public List<CupoLiberado> cerrarVigentesDelPedido(long pedidoId, String estado, Instant ahora) {
        return jdbc.query("""
                UPDATE inscripcion SET estado = ?, cancelada_en = ?
                WHERE pedido_id = ? AND estado IN ('RESERVADA', 'CONFIRMADA')
                RETURNING evento_id, cantidad
                """, (rs, n) -> new CupoLiberado(rs.getLong("evento_id"), rs.getInt("cantidad")),
                estado, AgendaYCuposJdbcAdapter.utc(ahora), pedidoId);
    }

    /** Snapshot de la inscripción + tipo y fecha vigente del evento (para saber si aún se puede cancelar). */
    private static final String VISTA = """
            SELECT i.id, i.evento_id, e.tipo, i.cliente_id, i.pedido_id, i.cantidad, i.estado, i.titulo_snapshot,
                   i.fecha_snapshot, i.lugar_snapshot, i.precio_snapshot, i.creado_en, i.cancelada_en,
                   (i.estado = 'CONFIRMADA' AND e.fecha_hora > ?) AS cancelable
            FROM inscripcion i JOIN evento e ON e.id = i.evento_id
            """;

    private static InscripcionVista vista(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new InscripcionVista(rs.getLong("id"), rs.getLong("evento_id"), TipoEvento.desdeSlug(rs.getString("tipo")),
                rs.getLong("cliente_id"), rs.getLong("pedido_id"), rs.getInt("cantidad"),
                EstadoInscripcion.valueOf(rs.getString("estado")), rs.getString("titulo_snapshot"),
                AgendaYCuposJdbcAdapter.instante(rs, "fecha_snapshot"), rs.getString("lugar_snapshot"),
                rs.getLong("precio_snapshot"), AgendaYCuposJdbcAdapter.instante(rs, "creado_en"),
                AgendaYCuposJdbcAdapter.instante(rs, "cancelada_en"), rs.getBoolean("cancelable"));
    }

    @Override
    public List<InscripcionVista> delCliente(long clienteId, Instant ahora) {
        // Próximas primero (la más cercana arriba); después las pasadas, de la más reciente a la más antigua.
        return jdbc.query(VISTA + """
                WHERE i.cliente_id = ?
                ORDER BY (i.fecha_snapshot >= ?) DESC,
                         CASE WHEN i.fecha_snapshot >= ? THEN i.fecha_snapshot END ASC,
                         i.fecha_snapshot DESC, i.id DESC
                """, (rs, n) -> vista(rs), AgendaYCuposJdbcAdapter.utc(ahora), clienteId,
                AgendaYCuposJdbcAdapter.utc(ahora), AgendaYCuposJdbcAdapter.utc(ahora));
    }

    @Override
    public List<InscripcionVista> delEvento(long eventoId, Instant ahora) {
        return jdbc.query(VISTA + """
                WHERE i.evento_id = ?
                ORDER BY (i.estado IN ('RESERVADA', 'CONFIRMADA')) DESC, i.creado_en, i.id
                """, (rs, n) -> vista(rs), AgendaYCuposJdbcAdapter.utc(ahora), eventoId);
    }

    @Override
    public Optional<InscripcionVista> porId(long id, Instant ahora) {
        return jdbc.query(VISTA + "WHERE i.id = ?", (rs, n) -> vista(rs), AgendaYCuposJdbcAdapter.utc(ahora), id)
                .stream().findFirst();
    }

    @Override
    public Optional<CupoLiberado> cancelarConfirmada(long inscripcionId, long clienteId, Instant ahora) {
        return jdbc.query("""
                UPDATE inscripcion i SET estado = 'CANCELADA', cancelada_en = ?
                FROM evento e
                WHERE i.id = ? AND i.cliente_id = ? AND i.estado = 'CONFIRMADA'
                  AND e.id = i.evento_id AND e.fecha_hora > ?
                RETURNING i.evento_id, i.cantidad
                """, (rs, n) -> new CupoLiberado(rs.getLong("evento_id"), rs.getInt("cantidad")),
                AgendaYCuposJdbcAdapter.utc(ahora), inscripcionId, clienteId, AgendaYCuposJdbcAdapter.utc(ahora))
                .stream().findFirst();
    }

    @Override
    public int cuposVigentes(long clienteId, long eventoId) {
        Integer total = jdbc.queryForObject("""
                SELECT coalesce(sum(cantidad), 0) FROM inscripcion
                WHERE cliente_id = ? AND evento_id = ? AND estado IN ('RESERVADA', 'CONFIRMADA')
                """, Integer.class, clienteId, eventoId);
        return total == null ? 0 : total;
    }
}
