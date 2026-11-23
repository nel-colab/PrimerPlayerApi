package cl.primerplayer.identidad.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 403 {@code email-not-verified}: el personal (VENDEDOR/ADMIN) necesita el correo verificado para usar el panel. */
public class CorreoSinVerificarException extends ExcepcionNegocio {

    public CorreoSinVerificarException() {
        super("email-not-verified", HttpStatus.FORBIDDEN,
                "Verifica tu correo (con el enlace que te enviamos o con Google) para trabajar en el panel.");
    }
}
