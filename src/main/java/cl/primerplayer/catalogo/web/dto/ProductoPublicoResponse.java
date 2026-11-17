package cl.primerplayer.catalogo.web.dto;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Forma del {@code Product} del frontend para la tienda pública. {@code imagenes}: URLs con la portada primero. */
public record ProductoPublicoResponse(
        long id,
        String nombre,
        String descripcion,
        @Schema(description = "CLP", example = "6990") long precio,
        @Schema(example = "tcg-pokemon") String categoria,
        String tipo,
        String idioma,
        String marca,
        String plataforma,
        String franquicia,
        String linea,
        @Schema(description = "Máximo por cliente; null = sin límite") Integer limitePorUsuario,
        int stock,
        int popularidad,
        @Schema(description = "Fecha de ingreso") LocalDate fecha,
        @Schema(description = "URL de la portada") String imagen,
        List<String> imagenes) {
}
