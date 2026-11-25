package cl.primerplayer.catalogo.web.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cuerpo de POST y PUT. Sin {@code stock}: se carga con {@code POST /admin/inventario/{id}/ajustes}. */
public record ProductoRequest(
        @Schema(example = "Sobre Pokémon Black Bolt") @NotBlank @Size(max = 200) String nombre,
        @Schema(example = "Sobre de 10 cartas.\n\n- Idioma inglés") @Size(max = 10_000) String descripcion,
        @Schema(example = "4990", description = "CLP entero") @NotNull @Min(0) @Max(100_000_000) Long precio,
        @Schema(example = "tcg", description = "slug de una categoría hoja") @NotBlank @Size(max = 60) String categoria,
        @Schema(example = "Sobre") @Size(max = 40) String tipo,
        @Schema(example = "Inglés") @Size(max = 30) String idioma,
        @Schema(example = "The Pokémon Company") @Size(max = 60) String marca,
        @Size(max = 30) String plataforma,
        @Schema(example = "Pokémon") @Size(max = 60) String franquicia,
        @Schema(example = "Scarlet & Violet") @Size(max = 80) String linea,
        @Schema(example = "0820650858306") @Pattern(regexp = "^$|^([0-9]{8}|[0-9]{12}|[0-9]{13})$",
                message = "debe tener 8, 12 o 13 dígitos") String codigoBarras,
        @Schema(example = "4", description = "null = ilimitado") @Min(1) @Max(999) Integer limitePorUsuario,
        @Schema(defaultValue = "false") Boolean publicado,
        @Size(max = 5) List<@Valid @NotNull ImagenDto> imagenes) {
}
