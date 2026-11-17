package cl.primerplayer.identidad.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

public class EmailEnUsoException extends ExcepcionNegocio {
    public EmailEnUsoException() {
        super("email-taken", HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo.");
    }
}
