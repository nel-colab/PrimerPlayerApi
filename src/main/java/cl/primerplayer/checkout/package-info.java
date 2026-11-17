/**
 * Módulo checkout: carrito, checkout web y retorno de Webpay. No tiene tablas: orquesta {@code ventas} (pedido,
 * stock, cupos, límites) y {@code pagos} (Webpay), cuidando que ninguna llamada HTTP quede dentro de una transacción.
 *
 * <p>Flujo: {@code POST /checkout} crea el pedido PENDIENTE_PAGO (transacción) → crea el pago en Webpay (sin
 * transacción) → registra el token (transacción). Webpay devuelve al cliente a {@code /pagos/webpay/retorno}: se
 * confirma con Transbank (sin transacción) y se aplica el resultado (transacción). Una tarea cada minuto vence las
 * reservas sin pago y devuelve stock y cupos.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Checkout",
    allowedDependencies = { "shared", "ventas :: api", "pagos :: api" }
)
package cl.primerplayer.checkout;
