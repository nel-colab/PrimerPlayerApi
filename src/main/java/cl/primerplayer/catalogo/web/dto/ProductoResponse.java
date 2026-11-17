package cl.primerplayer.catalogo.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
        int version,
        Instant actualizadoEn) {
}
