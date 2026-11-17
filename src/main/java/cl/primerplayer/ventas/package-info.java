/**
 * Módulo ventas: pedidos web y de mostrador ({@code pedido}, {@code pedido_item}, secuencia {@code pedido_codigo_seq}).
 *
 * <p>Arquitectura hexagonal: {@code api} (puertos de entrada), {@code domain} (reglas de estado puras),
 * {@code application} (casos de uso y puertos de salida), {@code infrastructure} (JPA) y {@code web} (REST).
 * Los pagos viven en el módulo {@code pagos}; aquí solo el pedido y sus ítems.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Ventas",
    allowedDependencies = { "shared", "identidad :: api", "catalogo :: api", "inventario :: api", "eventos :: api",
                            "pagos :: api" }
)
package cl.primerplayer.ventas;
