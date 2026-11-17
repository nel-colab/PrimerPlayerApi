package cl.primerplayer.shared.api;

import org.springframework.http.HttpStatus;


public class ConflictoVersionException extends ExcepcionNegocio {
    public ConflictoVersionException() {
        super("version-conflict", HttpStatus.CONFLICT, "Otra persona modificó este registro. Recárgalo antes de guardar.");
    }
}
