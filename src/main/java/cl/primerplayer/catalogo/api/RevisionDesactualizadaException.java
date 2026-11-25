package cl.primerplayer.catalogo.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/**
 * 409 {@code review-outdated}: el producto cambió después de que el VENDEDOR propuso la edición. Aplicarla pisaría
 * esos cambios, así que no se aprueba: se rechaza y el vendedor la propone de nuevo sobre la versión actual.
 */
public class RevisionDesactualizadaException extends ExcepcionNegocio {
    public RevisionDesactualizadaException() {
        super("review-outdated", HttpStatus.CONFLICT,
                "El producto cambió después de proponer esta edición. Recházala y pide que se proponga de nuevo.");
    }
}
