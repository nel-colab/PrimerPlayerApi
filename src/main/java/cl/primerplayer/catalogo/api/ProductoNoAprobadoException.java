package cl.primerplayer.catalogo.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 409 {@code product-not-approved}: un VENDEDOR intentó publicar un producto que un ADMIN aún no aprueba. */
public class ProductoNoAprobadoException extends ExcepcionNegocio {
    public ProductoNoAprobadoException() {
        super("product-not-approved", HttpStatus.CONFLICT,
                "El producto aún no está aprobado: se publicará cuando un administrador lo apruebe.");
    }
}
