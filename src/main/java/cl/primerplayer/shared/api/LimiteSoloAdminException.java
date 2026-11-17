package cl.primerplayer.shared.api;

import org.springframework.http.HttpStatus;


public class LimiteSoloAdminException extends ExcepcionNegocio {
    public LimiteSoloAdminException() {
        super("limit-admin-only", HttpStatus.FORBIDDEN, "Solo un administrador puede cambiar un límite por cliente ya configurado.");
    }
}
