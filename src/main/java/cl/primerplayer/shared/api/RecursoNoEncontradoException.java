package cl.primerplayer.shared.api;

import org.springframework.http.HttpStatus;

/** 404 con {@code code = item-unavailable}: no existe, está eliminado o no es visible para quien pregunta. */
public class RecursoNoEncontradoException extends ExcepcionNegocio {

    public RecursoNoEncontradoException(String detalle) {
        super("item-unavailable", HttpStatus.NOT_FOUND, detalle);
    }
}
