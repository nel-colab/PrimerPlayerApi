package cl.primerplayer.catalogo.api;

import java.util.List;

/**
 * Datos editables de un producto (crear o reemplazar). El stock no está aquí: lo escribe solo {@code inventario}.
 *
 * @param categoria          slug de una categoría hoja
 * @param franquicia         nombre de una franquicia existente, o {@code null}
 * @param limitePorCliente   {@code null} = ilimitado
 */
public record DatosProducto(
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
        boolean publicado,
        List<ImagenProducto> imagenes) {

    public DatosProducto {
        imagenes = imagenes == null ? List.of() : List.copyOf(imagenes);
    }

    /** Imagen por URL externa {@code https://}. TODO: {@code archivoId} cuando exista el módulo media. */
    /**
     * Imagen del producto: una URL {@code https://} externa o un {@code archivoId} subido a media (al leer, {@code url}
     * viene resuelta en ambos casos).
     */
    public record ImagenProducto(String url, boolean portada, java.util.UUID archivoId) {

        public ImagenProducto(String url, boolean portada) {
            this(url, portada, null);
        }
    }
}
