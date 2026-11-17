package cl.primerplayer.identidad.api;

import java.time.Instant;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

public class CuentaBloqueadaException extends ExcepcionNegocio {

    private final Instant bloqueadoHasta;

    public CuentaBloqueadaException(Instant bloqueadoHasta) {
        super("account-locked", HttpStatus.LOCKED,
              "La cuenta está bloqueada temporalmente por demasiados intentos fallidos.");
        this.bloqueadoHasta = bloqueadoHasta;
    }

    public Instant bloqueadoHasta() { return bloqueadoHasta; }
}
