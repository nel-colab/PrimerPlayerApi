/**
 * Módulo POS (venta en mostrador). No tiene tablas: orquesta {@code ventas} (pedido canal tienda, stock y cupos) y
 * {@code pagos} (pago mixto) en UNA transacción, así una venta nunca queda descontada sin sus pagos ni al revés.
 *
 * <p>El "POS" físico es solo una pistola lectora de códigos de barras que actúa como teclado: escribe el código donde
 * está el foco y presiona Enter. La búsqueda por código es un GET común ({@code /admin/pos/productos/codigo/{codigo}},
 * en el módulo catálogo).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "POS",
    allowedDependencies = { "shared", "ventas :: api", "pagos :: api" }
)
package cl.primerplayer.pos;
