package cl.primerplayer.catalogo.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

public class FranquiciaDuplicadaException extends ExcepcionNegocio {
    public FranquiciaDuplicadaException() {
        super("franchise-exists", HttpStatus.CONFLICT, "Ya existe una franquicia con ese nombre.");
    }
}
