package cl.primerplayer.catalogo.api;

/**
 * SPI que catálogo necesita y no implementa: el umbral de "stock bajo" pertenece a {@code inventario}
 * (tabla {@code config_inventario}). Catálogo no puede depender de inventario, así que declara el puerto aquí e
 * inventario —que sí puede depender de catálogo— lo implementa (inversión de dependencias).
 */
public interface UmbralStockBajo {

    /** Un producto con {@code 0 < stock <= umbral} está en stock bajo. */
    int umbralStockBajo();
}
