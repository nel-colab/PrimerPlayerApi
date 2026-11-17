package cl.primerplayer.eventos.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import cl.primerplayer.eventos.api.TipoEvento;
import cl.primerplayer.eventos.domain.Bloque;

/** Puerto de salida: consultas de traslape de la agenda y serialización de sus cambios. */
public interface AgendaRepositorio {

    /**
     * Serializa los cambios de agenda hasta el fin de la transacción (advisory lock). Así dos aprobaciones o
     * creaciones simultáneas no validan el mismo hueco a la vez. Requiere transacción.
     */
    void bloquearAgenda();

    /**
     * Eventos vigentes (no eliminados, publicados o no) cuyo bloque se traslapa con {@code [desde, hasta)}.
     * Los eventos sin hora de término ocupan {@code duracionEstimada}.
     */
    List<Ocupacion> traslapes(Instant desde, Instant hasta, Duration duracionEstimada, Long excluirEventoId);

    record Ocupacion(long eventoId, TipoEvento tipo, Bloque bloque, boolean publicado) {}
}
