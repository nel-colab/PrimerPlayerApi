package cl.primerplayer.identidad.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 409 {@code google-email-mismatch}: la cuenta de Google confirma un correo distinto al de la cuenta de la tienda. */
public class GoogleCorreoDistintoException extends ExcepcionNegocio {

    public GoogleCorreoDistintoException() {
        super("google-email-mismatch", HttpStatus.CONFLICT,
                "La cuenta de Google que elegiste usa otro correo. Elige la que tenga el mismo correo de tu cuenta.");
    }
}
