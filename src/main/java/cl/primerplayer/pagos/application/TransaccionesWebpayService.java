package cl.primerplayer.pagos.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.pagos.api.PasarelaWebpay.ResultadoWebpay;
import cl.primerplayer.pagos.api.TransaccionesWebpay;
import cl.primerplayer.pagos.application.port.out.TransaccionWebpayRepositorio;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class TransaccionesWebpayService implements TransaccionesWebpay {

    private static final Set<EstadoTransaccion> CIERRES_SIN_PAGO =
            Set.of(EstadoTransaccion.ANULADA_USUARIO, EstadoTransaccion.ERROR, EstadoTransaccion.EXPIRADA);

    private final TransaccionWebpayRepositorio repo;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(long pedidoId, String buyOrder, String sessionId, String token, long monto) {
        repo.insertarCreada(pedidoId, buyOrder, sessionId, token, monto, clock.instant());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<TransaccionWebpay> porToken(String token) {
        return repo.porToken(token, false);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<TransaccionWebpay> bloquearPorToken(String token) {
        return repo.porToken(token, true);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<TransaccionWebpay> bloquearPorBuyOrder(String buyOrder) {
        return repo.porBuyOrder(buyOrder, true);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<TransaccionWebpay> creadaDelPedido(long pedidoId) {
        return repo.creadaDelPedido(pedidoId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void aplicar(long transaccionId, ResultadoWebpay resultado, boolean aceptado) {
        Instant ahora = clock.instant();
        TransaccionWebpay t = repo.porId(transaccionId);
        repo.guardarResultado(transaccionId, aceptado ? EstadoTransaccion.AUTORIZADA : EstadoTransaccion.RECHAZADA,
                resultado, ahora);
        if (aceptado) {
            repo.insertarPagoWebpay(t.pedidoId(), t.monto(), ahora);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void cerrar(long transaccionId, EstadoTransaccion estado) {
        if (!CIERRES_SIN_PAGO.contains(estado)) {
            throw new IllegalArgumentException("Cierre sin pago inválido: " + estado);
        }
        repo.cerrar(transaccionId, estado, clock.instant());
    }
}
