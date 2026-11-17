package cl.primerplayer.shared.api;

import org.springframework.http.HttpStatus;


public class VersionRequeridaException extends ExcepcionNegocio {
    public VersionRequeridaException() {
        super("precondition-required", HttpStatus.PRECONDITION_REQUIRED, "Envía la versión del registro en el encabezado If-Match.");
    }
}
