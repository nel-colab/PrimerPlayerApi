package cl.primerplayer.ventas.events;

/** Un pedido web quedó pagado (Webpay autorizado o total 0). Lo escucha notificaciones para enviar la confirmación. */
public record PedidoConfirmado(long pedidoId, String codigo, long clienteId, long total) {}
