/**
 * Módulo catálogo: productos (excepto escribir su stock), categorías, franquicias e imágenes.
 *
 * <p>Arquitectura hexagonal: {@code api} (puertos de entrada + SPI {@link cl.primerplayer.catalogo.api.UmbralStockBajo}),
 * {@code domain} (modelo puro), {@code application} (casos de uso y puertos de salida),
 * {@code infrastructure} (JPA) y {@code web} (REST).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Catálogo",
    allowedDependencies = { "shared", "media :: api" }
)
package cl.primerplayer.catalogo;
