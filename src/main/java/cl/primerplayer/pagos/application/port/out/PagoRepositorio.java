package cl.primerplayer.pagos.application.port.out;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import cl.primerplayer.pagos.api.ConsultarPagosUseCase.PagoVista;
import cl.primerplayer.pagos.api.PagosMostrador.PagoMostrador;

/** Puerto de salida: tabla {@code pago} (y lectura de la transacción Webpay autorizada). */
public interface PagoRepositorio {

    /**
     * @param vuelto      vuelto a guardar (solo efectivo)
     * @param saldoAFavor valor de la carta − monto (solo cartas-tcg)
     * @throws ComprobanteInexistenteException si el comprobante de transferencia no existe
     */
    void insertarMostrador(long pedidoId, PagoMostrador pago, Long vuelto, long saldoAFavor, Instant ahora);

    List<PagoFila> dePedidos(Collection<Long> pedidoIds);

    record PagoFila(long pedidoId, PagoVista pago) {}

    class ComprobanteInexistenteException extends RuntimeException {
        public ComprobanteInexistenteException() {
            super("comprobante inexistente");
        }
    }
}
