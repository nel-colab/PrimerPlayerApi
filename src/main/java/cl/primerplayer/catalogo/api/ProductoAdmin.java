package cl.primerplayer.catalogo.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import cl.primerplayer.catalogo.api.DatosProducto.ImagenProducto;

/**
 * Vista completa de un producto para el mantenedor (incluye no publicados, stock y versión).
 *
 * @param aprobacion      estado del alta (solo un producto APROBADO puede publicarse)
 * @param cambioPendiente hay una edición de un VENDEDOR esperando aprobación (los datos de aquí son los aprobados)
 */
public record ProductoAdmin(
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
        Integer limitePorCliente,
        int stock,
        int popularidad,
        LocalDate fechaIngreso,
        boolean publicado,
        List<ImagenProducto> imagenes,
        EstadoAprobacion aprobacion,
        boolean cambioPendiente,
        int version,
        Instant actualizadoEn) {
}
