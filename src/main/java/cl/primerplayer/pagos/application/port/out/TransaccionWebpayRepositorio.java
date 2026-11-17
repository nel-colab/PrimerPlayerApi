package cl.primerplayer.pagos.application.port.out;

import java.time.Instant;
import java.util.Optional;

import cl.primerplayer.pagos.api.PasarelaWebpay.ResultadoWebpay;
import cl.primerplayer.pagos.api.TransaccionesWebpay.EstadoTransaccion;
import cl.primerplayer.pagos.api.TransaccionesWebpay.TransaccionWebpay;

/** Puerto de salida: tablas {@code transaccion_webpay} y {@code pago}. Requiere transacción. */
public interface TransaccionWebpayRepositorio {

    void insertarCreada(long pedidoId, String buyOrder, String sessionId, String token, long monto, Instant ahora);

    Optional<TransaccionWebpay> porToken(String token, boolean bloquear);

    Optional<TransaccionWebpay> porBuyOrder(String buyOrder, boolean bloquear);

    Optional<TransaccionWebpay> creadaDelPedido(long pedidoId);

    /** @throws java.util.NoSuchElementException si no existe */
    TransaccionWebpay porId(long id);

    void guardarResultado(long id, EstadoTransaccion estado, ResultadoWebpay r, Instant ahora);

    void cerrar(long id, EstadoTransaccion estado, Instant ahora);

    /** Inserta el pago {@code webpay}; el trigger diferido exige que cuadre con el total del pedido al hacer COMMIT. */
    void insertarPagoWebpay(long pedidoId, long monto, Instant ahora);
}
