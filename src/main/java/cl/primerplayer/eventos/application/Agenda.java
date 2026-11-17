package cl.primerplayer.eventos.application;

import java.util.List;

import org.springframework.stereotype.Component;

import cl.primerplayer.eventos.api.BloqueOcupadoException;
import cl.primerplayer.eventos.application.port.out.AgendaRepositorio;
import cl.primerplayer.eventos.application.port.out.AgendaRepositorio.Ocupacion;
import cl.primerplayer.eventos.domain.Bloque;
import cl.primerplayer.eventos.domain.Evento;
import lombok.RequiredArgsConstructor;

/**
 * Regla de agenda: durante un cumpleaños la tienda está cerrada al público.
 * <ul>
 *   <li>Un cumpleaños no se traslapa con <b>ningún</b> otro evento vigente (ni con otro cumpleaños).</li>
 *   <li>Un evento normal no se traslapa con un cumpleaños; entre eventos normales sí pueden convivir.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
class Agenda {

    private final AgendaRepositorio agenda;
    private final EventosProperties props;

    /** Llamar al inicio de cada transacción que cambia la agenda. */
    void bloquear() {
        agenda.bloquearAgenda();
    }

    void exigirLibre(Evento evento, Long excluirEventoId) {
        Bloque bloque = evento.bloque(props.duracionEstimada());
        List<Ocupacion> choques = agenda.traslapes(bloque.inicio(), bloque.fin(), props.duracionEstimada(),
                excluirEventoId);
        boolean conflicto = evento.esCumpleanos()
                ? !choques.isEmpty()
                : choques.stream().anyMatch(o -> !o.tipo().vendeCupos());
        if (conflicto) {
            throw new BloqueOcupadoException();
        }
    }

    /** Para un cumpleaños que se solicita: el bloque no puede chocar con nada vigente. */
    void exigirLibreParaCumpleanos(Bloque bloque) {
        if (!agenda.traslapes(bloque.inicio(), bloque.fin(), props.duracionEstimada(), null).isEmpty()) {
            throw new BloqueOcupadoException();
        }
    }

    List<Ocupacion> ocupaciones(Bloque rango) {
        return agenda.traslapes(rango.inicio(), rango.fin(), props.duracionEstimada(), null);
    }
}
