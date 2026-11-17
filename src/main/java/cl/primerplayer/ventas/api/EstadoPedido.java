package cl.primerplayer.ventas.api;

/** Estados del pedido (columna {@code pedido.estado}). Las transiciones válidas viven en el dominio. */
public enum EstadoPedido {
    PENDIENTE_PAGO, PAGADO, LISTO_RETIRO, ENTREGADO, RECHAZADO, EXPIRADO, ANULADO, REEMBOLSADO
}
