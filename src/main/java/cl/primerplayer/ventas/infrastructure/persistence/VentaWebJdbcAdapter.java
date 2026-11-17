package cl.primerplayer.ventas.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import cl.primerplayer.ventas.api.EstadoPedido;
import cl.primerplayer.ventas.application.port.out.VentaWebRepositorio;
import lombok.RequiredArgsConstructor;

/** Adaptador JDBC de la venta web: advisory lock, secuencia de códigos e INSERT/UPDATE condicionales. */
@Component
@RequiredArgsConstructor
class VentaWebJdbcAdapter implements VentaWebRepositorio {

    /** Espacio de nombres del advisory lock por cliente (distinto del lock de la agenda de eventos). */
    private static final int LOCK_CLIENTE = 0x5050_4356;   // "PPCV"

    private final JdbcTemplate jdbc;

    @Override
    public void bloquearCliente(long clienteId) {
        jdbc.query("SELECT pg_advisory_xact_lock(?, ?)", rs -> null, LOCK_CLIENTE, (int) (clienteId ^ (clienteId >>> 32)));
    }

    @Override
    public Optional<PedidoIdempotente> porIdempotencia(long clienteId, UUID key) {
        return jdbc.query("""
                SELECT id, codigo, estado, total, expira_en, idempotency_hash FROM pedido
                WHERE cliente_id = ? AND idempotency_key = ?
                """, (rs, n) -> new PedidoIdempotente(rs.getLong("id"), rs.getString("codigo"),
                        EstadoPedido.valueOf(rs.getString("estado")), rs.getLong("total"), instante(rs, "expira_en"),
                        rs.getString("idempotency_hash")),
                clienteId, key).stream().findFirst();
    }

    @Override
    public int cantidadComprada(long clienteId, long productoId) {
        Integer total = jdbc.queryForObject("""
                SELECT coalesce(sum(i.cantidad), 0) FROM pedido_item i JOIN pedido p ON p.id = i.pedido_id
                WHERE p.cliente_id = ? AND i.producto_id = ?
                  AND p.estado IN ('PENDIENTE_PAGO', 'PAGADO', 'LISTO_RETIRO', 'ENTREGADO')
                """, Integer.class, clienteId, productoId);
        return total == null ? 0 : total;
    }

    @Override
    public String siguienteCodigo(int anio) {
        Long n = jdbc.queryForObject("SELECT nextval('pedido_codigo_seq')", Long.class);
        return "PP-%d-%06d".formatted(anio, n);
    }

    @Override
    public long insertar(NuevoPedido p) {
        Long id = jdbc.queryForObject("""
                INSERT INTO pedido (codigo, cliente_id, vendedor_id, canal, estado, total, idempotency_key,
                                    idempotency_hash, expira_en, pagado_en, creado_en, actualizado_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, p.codigo(), p.clienteId(), p.vendedorId(), p.canal(), p.estado().name(), p.total(),
                p.idempotencyKey(), p.hash(), ts(p.expiraEn()), ts(p.pagadoEn()), ts(p.creadoEn()), ts(p.creadoEn()));
        for (ItemNuevo i : p.items()) {
            boolean producto = "product".equals(i.tipo());
            jdbc.update("""
                    INSERT INTO pedido_item (pedido_id, tipo, producto_id, evento_id, nombre, cantidad, precio_unitario)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, id, i.tipo(), producto ? i.itemId() : null, producto ? null : i.itemId(), i.nombre(),
                    i.cantidad(), i.precioUnitario());
        }
        return id;
    }

    @Override
    public boolean marcarPagado(long pedidoId, Instant ahora) {
        return jdbc.update("""
                UPDATE pedido SET estado = 'PAGADO', pagado_en = ?, actualizado_en = ?
                WHERE id = ? AND estado = 'PENDIENTE_PAGO'
                """, ts(ahora), ts(ahora), pedidoId) == 1;
    }

    @Override
    public boolean cerrarPendiente(long pedidoId, EstadoPedido destino, Instant ahora) {
        return jdbc.update("""
                UPDATE pedido SET estado = ?, actualizado_en = ?
                WHERE id = ? AND estado = 'PENDIENTE_PAGO'
                """, destino.name(), ts(ahora), pedidoId) == 1;
    }

    @Override
    public List<Long> vencidos(Instant ahora, int limite) {
        return jdbc.queryForList("""
                SELECT id FROM pedido WHERE estado = 'PENDIENTE_PAGO' AND expira_en < ?
                ORDER BY expira_en LIMIT ?
                """, Long.class, ts(ahora), limite);
    }

    @Override
    public List<ProductoDelPedido> productosDe(long pedidoId) {
        return jdbc.query("""
                SELECT producto_id, cantidad FROM pedido_item WHERE pedido_id = ? AND tipo = 'product' ORDER BY id
                """, (rs, n) -> new ProductoDelPedido(rs.getLong("producto_id"), rs.getInt("cantidad")), pedidoId);
    }

    @Override
    public java.util.Map<Long, ResumenPedido> resumenes(java.util.Collection<Long> pedidoIds) {
        if (pedidoIds.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.Map<Long, ResumenPedido> r = new java.util.HashMap<>();
        jdbc.query("SELECT id, codigo, canal FROM pedido WHERE id = ANY (?)",
                rs -> { r.put(rs.getLong("id"), new ResumenPedido(rs.getString("codigo"), rs.getString("canal"))); },
                (Object) pedidoIds.toArray(Long[]::new));
        return r;
    }

    @Override
    public String codigoDe(long pedidoId) {
        return jdbc.queryForObject("SELECT codigo FROM pedido WHERE id = ?", String.class, pedidoId);
    }

    /** UTC explícito: no depende de la zona horaria de la JVM ni de la sesión. */
    private static OffsetDateTime ts(Instant i) {
        return i == null ? null : i.atOffset(ZoneOffset.UTC);
    }

    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime o = rs.getObject(columna, OffsetDateTime.class);
        return o == null ? null : o.toInstant();
    }
}
