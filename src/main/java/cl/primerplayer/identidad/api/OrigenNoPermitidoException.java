package cl.primerplayer.identidad.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 403 {@code forbidden}: falta {@code X-Requested-With: pp-web} en una ruta que usa la cookie (defensa CSRF). */
public class OrigenNoPermitidoException extends ExcepcionNegocio {

    public OrigenNoPermitidoException() {
        super("forbidden", HttpStatus.FORBIDDEN, "Solicitud no permitida desde este origen.");
    }
}
