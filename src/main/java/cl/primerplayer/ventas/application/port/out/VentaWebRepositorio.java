package cl.primerplayer.ventas.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import cl.primerplayer.ventas.api.EstadoPedido;

/** Puerto de salida: escritura de pedidos web. Todo requiere transacción. */
public interface VentaWebRepositorio {

    /** Serializa los checkouts del mismo cliente hasta el fin de la transacción (límite por cliente sin carreras). */
    void bloquearCliente(long clienteId);

    Optional<PedidoIdempotente> porIdempotencia(long clienteId, UUID idempotencyKey);

    /** Unidades del producto en pedidos válidos del cliente (PENDIENTE_PAGO, PAGADO, LISTO_RETIRO, ENTREGADO). */
    int cantidadComprada(long clienteId, long productoId);

    /** {@code PP-<año>-<6 dígitos>} desde {@code pedido_codigo_seq} (global, no se reinicia). */
    String siguienteCodigo(int anio);

    /** Inserta pedido e ítems. @return id del pedido */
    long insertar(NuevoPedido pedido);

    /** PENDIENTE_PAGO → PAGADO con {@code pagado_en}. @return {@code false} si ya no estaba pendiente */
    boolean marcarPagado(long pedidoId, Instant ahora);

    /** PENDIENTE_PAGO → {@code destino}. @return {@code false} si ya no estaba pendiente */
    boolean cerrarPendiente(long pedidoId, EstadoPedido destino, Instant ahora);

    List<Long> vencidos(Instant ahora, int limite);

    /** Productos del pedido (para reponer stock). */
    List<ProductoDelPedido> productosDe(long pedidoId);

    String codigoDe(long pedidoId);

    /** Código y canal de varios pedidos en una consulta (para listar inscripciones). */
    java.util.Map<Long, ResumenPedido> resumenes(java.util.Collection<Long> pedidoIds);

    record ResumenPedido(String codigo, String canal) {}

    record PedidoIdempotente(long id, String codigo, EstadoPedido estado, long total, Instant expiraEn, String hash) {}

    /**
     * @param canal      {@code web} (PENDIENTE_PAGO con {@code expiraEn}) o {@code tienda} (con vendedor, ya pagado)
     * @param vendedorId obligatorio si {@code canal = tienda}
     * @param pagadoEn   {@code null} si queda pendiente de pago
     */
    record NuevoPedido(String codigo, long clienteId, Long vendedorId, String canal, EstadoPedido estado, long total,
                       UUID idempotencyKey, String hash, Instant expiraEn, Instant pagadoEn, Instant creadoEn,
                       List<ItemNuevo> items) {}

    /** @param tipo {@code product} o {@code event} */
    record ItemNuevo(String tipo, long itemId, String nombre, int cantidad, long precioUnitario) {}

    record ProductoDelPedido(long productoId, int cantidad) {}
}
