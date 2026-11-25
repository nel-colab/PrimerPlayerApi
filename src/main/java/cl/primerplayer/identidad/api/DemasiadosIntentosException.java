package cl.primerplayer.identidad.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 429 {@code too-many-attempts}: demasiados códigos errados en la última hora para esta cuenta. */
public class DemasiadosIntentosException extends ExcepcionNegocio {
    public DemasiadosIntentosException() {
        super("too-many-attempts", HttpStatus.TOO_MANY_REQUESTS,
                "Demasiados intentos con códigos incorrectos. Espera una hora y pide un código nuevo.");
    }
}
