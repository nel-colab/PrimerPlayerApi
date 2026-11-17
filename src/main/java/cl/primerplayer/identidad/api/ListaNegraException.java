package cl.primerplayer.identidad.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 403 {@code blacklisted}: la cuenta está en lista negra y no puede comprar ni solicitar cumpleaños. */
public class ListaNegraException extends ExcepcionNegocio {

    public ListaNegraException() {
        super("blacklisted", HttpStatus.FORBIDDEN,
                "Tu cuenta no puede realizar compras ni solicitudes. Comunícate con la tienda.");
    }
}
