package cl.primerplayer.pagos.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 502 {@code payment-provider-error}: Webpay no respondió o rechazó la operación. El pedido no queda cobrado. */
public class PasarelaNoDisponibleException extends ExcepcionNegocio {

    public PasarelaNoDisponibleException(String detalle) {
        super("payment-provider-error", HttpStatus.BAD_GATEWAY, detalle);
    }
}
