package cl.primerplayer.ventas.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 409 {@code order-invalid-state}: el pedido no puede pasar del estado actual al pedido. */
public class EstadoPedidoInvalidoException extends ExcepcionNegocio {

    public EstadoPedidoInvalidoException(String detalle) {
        super("order-invalid-state", HttpStatus.CONFLICT, detalle);
    }
}
