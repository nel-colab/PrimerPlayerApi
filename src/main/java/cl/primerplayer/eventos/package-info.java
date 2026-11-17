/**
 * Módulo eventos: agenda de la tienda (torneos, lanzamientos, cumpleaños), cupos atómicos y solicitudes de
 * cumpleaños que la tienda aprueba o rechaza.
 *
 * <p>Arquitectura hexagonal: {@code api} (puertos de entrada), {@code events}, {@code domain} (modelo puro),
 * {@code application} (casos de uso + puertos de salida), {@code infrastructure} (JPA/JDBC) y {@code web} (REST).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Eventos",
    allowedDependencies = { "shared", "identidad :: api" }
)
package cl.primerplayer.eventos;
