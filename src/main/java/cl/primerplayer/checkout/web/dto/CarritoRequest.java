package cl.primerplayer.checkout.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Mismo formato para validar el carrito y para el checkout. Ítems repetidos: agrúpalos en una línea. */
public record CarritoRequest(@NotNull @Valid List<Linea> lineas) {

    public record Linea(
            @Schema(example = "product", description = "product | event") @NotNull String type,
            @Schema(example = "42") @NotNull Long itemId,
            @Schema(example = "2", description = "1 a 999") @NotNull Integer quantity) {}
}
