package cl.primerplayer.ventas.api;

/** Puerto de entrada: acciones de la tienda sobre un pedido (retiro en tienda). */
public interface GestionarPedidosUseCase {

    /**
     * Marca un pedido pagado como {@code LISTO_RETIRO} o {@code ENTREGADO}.
     *
     * @throws cl.primerplayer.shared.api.RecursoNoEncontradoException si el código no existe
     * @throws cl.primerplayer.shared.api.ValidacionException si {@code nuevo} no es LISTO_RETIRO ni ENTREGADO
     * @throws EstadoPedidoInvalidoException si el pedido no puede pasar a ese estado (409 {@code order-invalid-state})
     */
    PedidoVista marcarRetiro(String codigo, EstadoPedido nuevo);
}
