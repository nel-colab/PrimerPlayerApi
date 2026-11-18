package cl.primerplayer.ventas.domain;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cl.primerplayer.ventas.api.CanalPedido;
import cl.primerplayer.ventas.api.EstadoPedido;
import cl.primerplayer.ventas.api.EstadoPedidoInvalidoException;

/** Pedido (modelo puro). Las reglas de estado viven aquí; la persistencia aplica el cambio de forma atómica. */
public record Pedido(
        long id,
        String codigo,
        long clienteId,
        Long vendedorId,
        CanalPedido canal,
        EstadoPedido estado,
        long total,
        Instant creadoEn,
        Instant pagadoEn,
        Instant actualizadoEn,
        List<Item> items) {

    public Pedido {
        items = List.copyOf(items);
    }

    /** @param tipo {@code product} o {@code event} */
    public record Item(String tipo, long itemId, String nombre, int cantidad, long precioUnitario, long subtotal) {

        public boolean esProducto() {
            return "product".equals(tipo);
        }
    }

    /**
     * Ciclo de vida completo: PENDIENTE_PAGO → PAGADO → LISTO_RETIRO → ENTREGADO; además RECHAZADO, EXPIRADO y
     * ANULADO desde el pago pendiente, REEMBOLSADO desde un pedido pagado y ANULADO para una venta de mostrador.
     */
    private static final Map<EstadoPedido, Set<EstadoPedido>> TRANSICIONES = Map.of(
            EstadoPedido.PENDIENTE_PAGO, EnumSet.of(EstadoPedido.PAGADO, EstadoPedido.RECHAZADO,
                    EstadoPedido.EXPIRADO, EstadoPedido.ANULADO),
            EstadoPedido.PAGADO, EnumSet.of(EstadoPedido.LISTO_RETIRO, EstadoPedido.ENTREGADO,
                    EstadoPedido.REEMBOLSADO, EstadoPedido.ANULADO),
            EstadoPedido.LISTO_RETIRO, EnumSet.of(EstadoPedido.ENTREGADO, EstadoPedido.REEMBOLSADO),
            EstadoPedido.ENTREGADO, EnumSet.of(EstadoPedido.REEMBOLSADO, EstadoPedido.ANULADO));

    /** Estados que la tienda puede marcar a mano desde el panel (retiro en tienda). */
    public static final Set<EstadoPedido> ESTADOS_DE_RETIRO = EnumSet.of(EstadoPedido.LISTO_RETIRO, EstadoPedido.ENTREGADO);

    public static boolean puedePasar(EstadoPedido desde, EstadoPedido hacia) {
        return TRANSICIONES.getOrDefault(desde, Set.of()).contains(hacia);
    }

    /**
     * Valida el paso a LISTO_RETIRO o ENTREGADO hecho por la tienda para cualquier pedido web.
     *
     * @throws EstadoPedidoInvalidoException si el estado actual no lo permite o es de mostrador
     */
    public void validarRetiro(EstadoPedido nuevo) {
        if (!ESTADOS_DE_RETIRO.contains(nuevo)) {
            throw new IllegalArgumentException("Solo LISTO_RETIRO o ENTREGADO");
        }
        if (canal == CanalPedido.TIENDA) {
            throw new EstadoPedidoInvalidoException("Las ventas de mostrador se entregan en el momento.");
        }
        if (!puedePasar(estado, nuevo)) {
            throw new EstadoPedidoInvalidoException(
                    "El pedido %s está %s y no puede pasar a %s.".formatted(codigo, estado, nuevo));
        }
    }
}
