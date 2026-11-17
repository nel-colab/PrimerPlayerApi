package cl.primerplayer.ventas.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 403 {@code email-not-verified}: para comprar hay que haber verificado el correo (decisión de negocio 2026-11-13). */
public class CorreoNoVerificadoException extends ExcepcionNegocio {

    public CorreoNoVerificadoException() {
        super("email-not-verified", HttpStatus.FORBIDDEN,
                "Debes verificar tu correo antes de comprar. Revisa tu bandeja de entrada.");
    }
}
