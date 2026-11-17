package cl.primerplayer.checkout.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cl.primerplayer.checkout.api.CheckoutUseCase;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Cada minuto vence los pedidos PENDIENTE_PAGO cuya reserva expiró (consultando antes a Webpay por si el cliente pagó
 * y su retorno se perdió). ShedLock asegura una sola ejecución aunque haya varias instancias de la API.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pp.tareas.habilitadas", havingValue = "true", matchIfMissing = true)
class VencimientoReservasTarea {

    private final CheckoutUseCase checkout;

    @Scheduled(fixedDelayString = "${pp.checkout.vencimiento-intervalo:PT1M}", initialDelayString = "PT30S")
    @SchedulerLock(name = "checkout-vencer-reservas", lockAtMostFor = "PT5M")
    void ejecutar() {
        checkout.expirarVencidos();
    }
}
