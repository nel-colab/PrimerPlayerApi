/**
 * Módulo inventario: stock atómico (única columna de {@code producto} que escribe), kardex
 * ({@code movimiento_stock}) y umbral de stock bajo ({@code config_inventario}).
 *
 * <p>Arquitectura hexagonal. Implementa además el SPI {@code catalogo.api.UmbralStockBajo}.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Inventario",
    allowedDependencies = { "shared", "catalogo :: api" }
)
package cl.primerplayer.inventario;
