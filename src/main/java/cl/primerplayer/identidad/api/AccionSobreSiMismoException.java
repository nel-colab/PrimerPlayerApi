package cl.primerplayer.identidad.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 409 {@code self-action}: un administrador no puede cambiar su propio rol, banearse, ponerse en lista negra ni cerrar sus sesiones. */
public class AccionSobreSiMismoException extends ExcepcionNegocio {

    public AccionSobreSiMismoException() {
        super("self-action", HttpStatus.CONFLICT, "No puedes aplicar esta acción sobre tu propia cuenta.");
    }
}
