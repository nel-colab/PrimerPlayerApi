/**
 * Módulo pagos: Webpay Plus (Transbank) y registro de pagos ({@code transaccion_webpay}, {@code pago}).
 *
 * <p>Las llamadas HTTP a Transbank ({@link cl.primerplayer.pagos.api.PasarelaWebpay}) nunca ocurren dentro de una
 * transacción de base de datos; la escritura ({@link cl.primerplayer.pagos.api.TransaccionesWebpay}) exige la
 * transacción de quien orquesta ({@code checkout}). Este módulo no conoce pedidos: solo su id y su monto.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Pagos",
    allowedDependencies = { "shared" }
)
package cl.primerplayer.pagos;
