package cl.primerplayer.pagos.api;

import java.util.Optional;

import cl.primerplayer.pagos.api.PasarelaWebpay.ResultadoWebpay;

/**
 * Registro local de las transacciones Webpay ({@code transaccion_webpay}) y del pago ({@code pago}).
 * Los comandos exigen una transacción abierta por quien orquesta (propagation MANDATORY).
 */
public interface TransaccionesWebpay {

    /** Guarda la transacción recién creada en Transbank, en estado CREADA. */
    void registrar(long pedidoId, String buyOrder, String sessionId, String token, long monto);

    Optional<TransaccionWebpay> porToken(String token);

    /** Bloquea la fila ({@code FOR UPDATE}) para resolverla una sola vez aunque lleguen dos retornos a la vez. */
    Optional<TransaccionWebpay> bloquearPorToken(String token);

    Optional<TransaccionWebpay> bloquearPorBuyOrder(String buyOrder);

    /** La transacción aún CREADA del pedido, si existe. */
    Optional<TransaccionWebpay> creadaDelPedido(long pedidoId);

    /**
     * Guarda el resultado del commit/status. Si {@code aceptado}: AUTORIZADA y se registra el pago {@code webpay}
     * por el monto; si no: RECHAZADA.
     */
    void aplicar(long transaccionId, ResultadoWebpay resultado, boolean aceptado);

    /** Cierra una transacción sin resultado de pago: ANULADA_USUARIO, ERROR o EXPIRADA. */
    void cerrar(long transaccionId, EstadoTransaccion estado);

    record TransaccionWebpay(long id, long pedidoId, String buyOrder, String token, long monto, EstadoTransaccion estado) {

        public boolean pendiente() {
            return estado == EstadoTransaccion.CREADA;
        }
    }

    enum EstadoTransaccion { CREADA, AUTORIZADA, RECHAZADA, ANULADA_USUARIO, EXPIRADA, ERROR, REVERSADA, ANULADA }
}
