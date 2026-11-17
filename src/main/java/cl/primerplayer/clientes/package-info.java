/**
 * Módulo clientes: cuenta del cliente ({@code direccion}, {@code favorito}, {@code alerta_stock}). Cuando un producto
 * agotado vuelve a tener stock ({@code StockRepuesto}), marca las alertas y publica {@code AlertaStockDisparada}.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Clientes",
    allowedDependencies = { "shared", "identidad :: api", "catalogo :: api", "inventario :: events" }
)
package cl.primerplayer.clientes;
