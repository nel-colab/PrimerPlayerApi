package cl.primerplayer.identidad.api;

import java.time.Instant;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;
import cl.primerplayer.shared.api.FechasTienda;

/** 423 {@code account-banned}: la tienda suspendió la cuenta (temporal o permanente). Distinto del bloqueo por intentos. */
public class CuentaBaneadaException extends ExcepcionNegocio {

    public CuentaBaneadaException(String motivo, Instant hasta) {
        super("account-banned", HttpStatus.LOCKED, (hasta == null
                ? "Tu cuenta está suspendida de forma indefinida."
                : "Tu cuenta está suspendida hasta el " + FechasTienda.enZona(hasta).toLocalDate() + ".")
                + " Motivo: " + motivo);
    }
}
