package cl.primerplayer.identidad.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 401 {@code invalid-google-token}: el ID token de Google no es válido, venció, no es para esta tienda o Google no confirma el correo. */
public class GoogleTokenInvalidoException extends ExcepcionNegocio {

    public GoogleTokenInvalidoException() {
        super("invalid-google-token", HttpStatus.UNAUTHORIZED,
                "No pudimos confirmar tu identidad con Google. Vuelve a intentarlo.");
    }
}
