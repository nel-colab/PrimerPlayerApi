package cl.primerplayer.identidad.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** Mensaje deliberadamente genérico: no revela si el correo existe. */
public class CredencialesInvalidasException extends ExcepcionNegocio {
    public CredencialesInvalidasException() {
        super("unauthorized", HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos.");
    }
}
