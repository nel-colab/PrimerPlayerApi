package cl.primerplayer.catalogo.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

public class CodigoBarrasInmutableException extends ExcepcionNegocio {
    public CodigoBarrasInmutableException() {
        super("barcode-immutable", HttpStatus.CONFLICT, "Solo un administrador puede agregar o cambiar el código de barras de un producto existente.");
    }
}
