package cl.primerplayer.catalogo.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

public class CategoriaDuplicadaException extends ExcepcionNegocio {
    public CategoriaDuplicadaException() {
        super("category-exists", HttpStatus.CONFLICT, "Ya existe una categoría con ese nombre o identificador.");
    }
}
