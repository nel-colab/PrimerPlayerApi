/**
 * Módulo notificaciones: correos transaccionales (verificación, recuperación, pedido confirmado, alerta de stock).
 * Escucha eventos DESPUÉS del commit y envía fuera de toda transacción (regla 4); registra cada envío en
 * {@code correo_enviado} (sin duplicar el mismo correo al reintentar). Nadie lo llama directamente.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Notificaciones",
    allowedDependencies = { "shared", "identidad :: api", "identidad :: events", "ventas :: events", "clientes :: events" }
)
package cl.primerplayer.notificaciones;
