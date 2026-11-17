package cl.primerplayer.eventos.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 409 {@code request-not-pending}: la solicitud ya fue aprobada, rechazada o cancelada. */
public class SolicitudNoPendienteException extends ExcepcionNegocio {
    public SolicitudNoPendienteException(String estado) {
        super("request-not-pending", HttpStatus.CONFLICT, "La solicitud ya no está pendiente (estado " + estado + ").");
    }
}
