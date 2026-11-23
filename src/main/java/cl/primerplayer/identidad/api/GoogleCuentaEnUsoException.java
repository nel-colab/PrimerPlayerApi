package cl.primerplayer.identidad.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 409 {@code google-account-in-use}: la cuenta de Google o el correo ya están vinculados a otra cuenta. */
public class GoogleCuentaEnUsoException extends ExcepcionNegocio {

    public GoogleCuentaEnUsoException() {
        super("google-account-in-use", HttpStatus.CONFLICT,
                "Esa cuenta de Google ya está vinculada a otra cuenta de la tienda.");
    }
}
