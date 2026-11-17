package cl.primerplayer.identidad.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 409 {@code last-admin}: la acción dejaría la tienda sin ningún administrador activo. */
public class UltimoAdminException extends ExcepcionNegocio {

    public UltimoAdminException() {
        super("last-admin", HttpStatus.CONFLICT, "Debe quedar al menos un administrador activo en la tienda.");
    }
}
