package cl.primerplayer.identidad.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 400 {@code invalid-token}: el enlace de verificación o de recuperación no existe, venció o ya se usó. */
public class TokenInvalidoException extends ExcepcionNegocio {

    public TokenInvalidoException() {
        super("invalid-token", HttpStatus.BAD_REQUEST, "El enlace no es válido o ya venció. Solicita uno nuevo.");
    }
}
