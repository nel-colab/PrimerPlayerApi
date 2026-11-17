package cl.primerplayer.checkout.web.dto;

import java.util.List;

import cl.primerplayer.shared.api.ExcepcionNegocio.ErrorLinea;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resultado de revisar el carrito con precios y disponibilidad actuales. {@code errors[]} usa el mismo formato que los
 * errores {@code problem+json} ({@code line} = índice de la línea enviada).
 */
public record ValidacionCarritoResponse(
        List<Linea> lines,
        @Schema(description = "CLP") long total,
        boolean valid,
        List<ErrorLinea> errors) {

    /**
     * @param available  stock o cupos disponibles ({@code null} si el ítem no existe)
     * @param maxAllowed unidades que el cliente aún puede comprar por su límite ({@code null} = sin límite o sin sesión)
     */
    public record Linea(String type, long itemId, String name, int quantity, long unitPrice, long subtotal,
                        Integer available, Integer maxAllowed) {}
}
