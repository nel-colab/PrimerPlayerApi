package cl.primerplayer.pos.web.dto;

import java.util.List;

import cl.primerplayer.shared.api.ExcepcionNegocio.ErrorLinea;

/** Revisión de la venta antes de cobrar. {@code errors[]} con {@code line} = índice de la línea enviada. */
public record ValidacionPosResponse(List<Linea> lines, long total, boolean valid, List<ErrorLinea> errors) {

    /** @param available stock o cupos disponibles ({@code null} si el ítem no existe) */
    public record Linea(String type, long itemId, String name, int quantity, long unitPrice, long subtotal,
                        Integer available) {}
}
