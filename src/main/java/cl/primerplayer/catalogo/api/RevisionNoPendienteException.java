package cl.primerplayer.catalogo.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 409 {@code review-not-pending}: la revisión ya fue aprobada, rechazada o descartada. */
public class RevisionNoPendienteException extends ExcepcionNegocio {
    public RevisionNoPendienteException(String estado) {
        super("review-not-pending", HttpStatus.CONFLICT, "La revisión ya no está pendiente (estado " + estado + ").");
    }
}
