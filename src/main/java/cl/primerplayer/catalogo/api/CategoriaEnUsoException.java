package cl.primerplayer.catalogo.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

public class CategoriaEnUsoException extends ExcepcionNegocio {
    public CategoriaEnUsoException() {
        super("category-in-use", HttpStatus.CONFLICT, "La categoría ya se usó en productos y no se puede eliminar.");
    }
}
