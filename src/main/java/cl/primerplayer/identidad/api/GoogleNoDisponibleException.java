package cl.primerplayer.identidad.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 503 {@code google-unavailable}: falta el client id de Google o no se pudieron obtener sus llaves públicas. */
public class GoogleNoDisponibleException extends ExcepcionNegocio {

    public GoogleNoDisponibleException() {
        super("google-unavailable", HttpStatus.SERVICE_UNAVAILABLE,
                "El inicio de sesión con Google no está disponible en este momento.");
    }
}
