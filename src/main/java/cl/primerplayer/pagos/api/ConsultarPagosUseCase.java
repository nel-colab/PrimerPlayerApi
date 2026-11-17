package cl.primerplayer.pagos.api;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lectura de los pagos de varios pedidos (para mostrarlos dentro del pedido). */
public interface ConsultarPagosUseCase {

    /** Pagos por pedido, en orden de registro. Los pedidos sin pagos no aparecen en el mapa. */
    Map<Long, List<PagoVista>> dePedidos(Collection<Long> pedidoIds);

    /**
     * Un pago registrado. Los campos dependen del método; para {@code webpay} se agregan los datos de la transacción
     * autorizada (código de autorización, últimos 4 dígitos, tipo de pago y cuotas). Nunca hay número de tarjeta completo.
     */
    record PagoVista(String metodo, long monto, String numeroBoleta, UUID comprobanteId, String cartaNombre,
                     String cartaCodigo, Long cartaValor, long saldoAFavor, Long efectivoRecibido, Long vueltoEntregado,
                     String autorizacionWebpay, String tarjetaUltimos4, String tipoPagoWebpay, Integer cuotas,
                     Instant creadoEn) {}
}
