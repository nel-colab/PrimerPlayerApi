package cl.primerplayer.catalogo.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import cl.primerplayer.catalogo.api.EstadoAprobacion;
import io.swagger.v3.oas.annotations.media.Schema;

/** Forma del {@code Product} del frontend + campos del mantenedor ({@code stock}, {@code version}). */
public record ProductoResponse(
        long id,
        String nombre,
        String descripcion,
        long precio,
        String categoria,
        String tipo,
        String idioma,
        String marca,
        String plataforma,
        String franquicia,
        String linea,
        String codigoBarras,
        Integer limitePorUsuario,
        int stock,
        LocalDate fecha,
        boolean publicado,
        String imagen,
        List<ImagenDto> imagenes,
        @Schema(description = "PENDIENTE | APROBADO | RECHAZADO. Solo un producto APROBADO puede publicarse.")
        EstadoAprobacion aprobacion,
        @Schema(description = "Hay una edición de un vendedor esperando aprobación; estos datos son los aprobados.")
        boolean cambioPendiente,
        int version,
        Instant actualizadoEn) {
}
