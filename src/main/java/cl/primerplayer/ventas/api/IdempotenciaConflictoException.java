package cl.primerplayer.ventas.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 409 {@code idempotency-conflict}: la misma {@code Idempotency-Key} se usó con otro carrito. */
public class IdempotenciaConflictoException extends ExcepcionNegocio {

    public IdempotenciaConflictoException() {
        super("idempotency-conflict", HttpStatus.CONFLICT,
                "Esta clave de idempotencia ya se usó con otro carrito. Genera una nueva para un pedido distinto.");
    }
}
