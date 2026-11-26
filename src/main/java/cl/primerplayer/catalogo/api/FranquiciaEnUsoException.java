package cl.primerplayer.catalogo.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

public class FranquiciaEnUsoException extends ExcepcionNegocio {
    public FranquiciaEnUsoException() {
        super("franchise-in-use", HttpStatus.CONFLICT, "La franquicia ya se usó en productos y no se puede eliminar.");
    }
}
