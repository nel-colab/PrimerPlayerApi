package cl.primerplayer.catalogo.web.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Imagen de un producto: un {@code archivoId} subido con {@code POST /admin/media/imagenes} o una {@code url} https
 * externa (una de las dos). Al leer, {@code url} viene siempre resuelta.
 */
public record ImagenDto(
        @Schema(example = "https://cdn.primerplayer.cl/black-bolt.webp") @Size(max = 500) String url,
        boolean portada,
        @Schema(description = "id devuelto por POST /admin/media/imagenes") UUID archivoId) {
}
