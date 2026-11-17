package cl.primerplayer.identidad.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 401 {@code unauthorized} en {@code /auth/refresh}: hay que volver a iniciar sesión (salvo carrera, ver detalle). */
public class SesionInvalidaException extends ExcepcionNegocio {

    public SesionInvalidaException() {
        super("unauthorized", HttpStatus.UNAUTHORIZED, "La sesión expiró. Inicia sesión nuevamente.");
    }
}
