package cl.primerplayer.catalogo.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

public class CodigoBarrasEnUsoException extends ExcepcionNegocio {
    public CodigoBarrasEnUsoException(String codigo) {
        super("barcode-taken", HttpStatus.CONFLICT, "El código de barras " + codigo + " ya está asignado a otro producto.");
    }
}
