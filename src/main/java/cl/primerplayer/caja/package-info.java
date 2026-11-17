/**
 * Módulo caja: cierre diario de la caja física ({@code cierre_caja}, {@code cierre_caja_version}).
 *
 * <p>El resumen del día se calcula en el servidor a partir de las ventas válidas (módulo ventas, con sus pagos) del día
 * local de Chile {@code [00:00, 24:00)}. El cliente solo envía fondo inicial, efectivo contado y observaciones.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Caja",
    allowedDependencies = { "shared", "ventas :: api", "pagos :: api", "identidad :: api" }
)
package cl.primerplayer.caja;
