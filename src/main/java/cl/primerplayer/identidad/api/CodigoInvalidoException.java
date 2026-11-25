package cl.primerplayer.identidad.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/**
 * 400 {@code invalid-code}: el código no corresponde, venció, ya se usó o se agotaron sus intentos. El mismo error para
 * todos los casos (y para un correo no registrado): no revela qué cuentas existen.
 */
public class CodigoInvalidoException extends ExcepcionNegocio {
    public CodigoInvalidoException() {
        super("invalid-code", HttpStatus.BAD_REQUEST,
                "El código no es válido o ya venció. Revisa el último correo que te enviamos o pide un código nuevo.");
    }
}
