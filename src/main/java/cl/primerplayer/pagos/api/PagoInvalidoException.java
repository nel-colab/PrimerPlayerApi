package cl.primerplayer.pagos.api;

import java.util.List;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/**
 * 422 con un {@code code} de pago: {@code payments-mismatch}, {@code receipt-required}, {@code transfer-proof-required},
 * {@code tcg-card-required} o {@code cash-change-mismatch}. {@code errors[].line} es el índice del pago.
 */
public class PagoInvalidoException extends ExcepcionNegocio {

    public PagoInvalidoException(String code, String detalle, Integer indicePago) {
        super(code, HttpStatus.UNPROCESSABLE_ENTITY, detalle,
                indicePago == null ? List.of() : List.of(new ErrorLinea(indicePago, "payment", 0, code, null)));
    }
}
