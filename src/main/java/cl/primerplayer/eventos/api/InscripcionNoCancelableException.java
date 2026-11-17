package cl.primerplayer.eventos.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 409 {@code registration-not-cancellable}: la inscripción no está confirmada (pendiente de pago, cancelada o expirada). */
public class InscripcionNoCancelableException extends ExcepcionNegocio {

    public InscripcionNoCancelableException(String detalle) {
        super("registration-not-cancellable", HttpStatus.CONFLICT, detalle);
    }
}
