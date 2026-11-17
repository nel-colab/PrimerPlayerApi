package cl.primerplayer.catalogo.api;

import java.time.LocalDate;
import java.util.List;

import cl.primerplayer.catalogo.api.DatosProducto.ImagenProducto;

/**
 * Vista pública de un producto: solo publicados y no eliminados. No expone código de barras, versión ni auditoría.
 * {@code stock} se muestra para que la tienda indique "agotado" o "últimas unidades".
 */
public record ProductoPublico(
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
        Integer limitePorCliente,
        int stock,
        int popularidad,
        LocalDate fechaIngreso,
        List<ImagenProducto> imagenes) {

    public ProductoPublico {
        imagenes = imagenes == null ? List.of() : List.copyOf(imagenes);
    }
}
