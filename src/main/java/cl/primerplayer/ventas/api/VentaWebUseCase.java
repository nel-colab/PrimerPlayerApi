package cl.primerplayer.ventas.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import cl.primerplayer.shared.api.ExcepcionNegocio.ErrorLinea;

/**
 * Puerto de entrada de la venta web (lo usa {@code checkout}). Los comandos exigen una transacción abierta por quien
 * orquesta (propagation MANDATORY) y nunca llaman servicios externos: el pago con Webpay va fuera de la transacción.
 */
public interface VentaWebUseCase {

    int MAX_LINEAS = 50;
    int MAX_CANTIDAD = 999;

    /**
     * Revisa el carrito sin reservar nada: precios vigentes, stock, cupos y, si hay cliente, su límite de compra.
     * Nunca lanza errores de negocio por línea: los devuelve en {@code errores}.
     *
     * @param clienteId {@code null} para un visitante (no se evalúa el límite por cliente)
     */
    ValidacionCarrito validar(Long clienteId, List<LineaCarrito> lineas);

    /**
     * Crea el pedido {@code PENDIENTE_PAGO}: valida, descuenta stock (kardex VENTA_WEB), ocupa cupos e inscribe
     * (RESERVADA), todo atómico. Si el total es 0 (eventos gratuitos) queda {@code PAGADO} de inmediato.
     * Misma {@code idempotencyKey} + mismo {@code hashCuerpo} → devuelve el pedido existente ({@code existente = true}).
     *
     * @throws CorreoNoVerificadoException 403 {@code email-not-verified}
     * @throws CarritoInvalidoException {@code empty}, {@code invalid-quantity}, {@code item-unavailable},
     *         {@code out-of-stock}, {@code stock-limit}, {@code event-full}, {@code event-past}, {@code purchase-limit}
     * @throws IdempotenciaConflictoException 409 {@code idempotency-conflict}
     */
    PedidoCreado crearPendiente(long clienteId, List<LineaCarrito> lineas, UUID idempotencyKey, String hashCuerpo);

    /** PENDIENTE_PAGO → PAGADO e inscripciones CONFIRMADA. @return {@code false} si el pedido ya no estaba pendiente */
    boolean confirmarPago(long pedidoId);

    /**
     * PENDIENTE_PAGO → {@code destino} (RECHAZADO, ANULADO o EXPIRADO): repone el stock y libera cupos.
     *
     * @return {@code false} si el pedido ya no estaba pendiente (no hace nada)
     */
    boolean cerrarSinPago(long pedidoId, EstadoPedido destino);

    /** Pedidos PENDIENTE_PAGO cuya reserva ya venció, los más antiguos primero. */
    List<Long> vencidos(int limite);

    /** @param tipo {@code product} o {@code event} */
    record LineaCarrito(String tipo, long itemId, int cantidad) {

        public boolean esProducto() {
            return "product".equals(tipo);
        }
    }

    /**
     * @param disponible   stock o cupos disponibles ({@code null} si el ítem no existe)
     * @param maxPermitido unidades que el cliente aún puede comprar por su límite ({@code null} = sin límite o visitante)
     */
    record LineaValidada(String tipo, long itemId, String nombre, int cantidad, long precioUnitario, long subtotal,
                         Integer disponible, Integer maxPermitido) {}

    record ValidacionCarrito(List<LineaValidada> lineas, long total, List<ErrorLinea> errores) {

        public boolean valido() {
            return errores.isEmpty();
        }
    }

    record PedidoCreado(long id, String codigo, EstadoPedido estado, long total, Instant expiraEn, boolean existente) {}
}
