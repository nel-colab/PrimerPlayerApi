package cl.primerplayer.identidad.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 409 {@code already-verified}: el correo de la cuenta ya está verificado. */
public class YaVerificadoException extends ExcepcionNegocio {

    public YaVerificadoException() {
        super("already-verified", HttpStatus.CONFLICT, "El correo de esta cuenta ya está verificado.");
    }
}
