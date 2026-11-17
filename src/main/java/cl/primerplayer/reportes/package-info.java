/**
 * Módulo reportes (solo ADMIN, solo lectura): ventas por día, hora, día del mes y mes del año, por método de pago y
 * top 5 por unidades. Lee las ventas válidas del módulo ventas (con sus pagos) y agrupa en hora de Chile.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Reportes",
    allowedDependencies = { "shared", "ventas :: api", "pagos :: api" }
)
package cl.primerplayer.reportes;
