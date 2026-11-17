package cl.primerplayer.eventos.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.eventos.api.ConsultarEventosUseCase;
import cl.primerplayer.eventos.api.ConsultarInscripcionesUseCase;
import cl.primerplayer.eventos.api.CuposInsuficientesException;
import cl.primerplayer.eventos.api.CuposOperaciones;
import cl.primerplayer.eventos.api.InscripcionNoCancelableException;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import cl.primerplayer.eventos.api.CuposOperaciones.LineaCupo;
import cl.primerplayer.eventos.api.InscripcionesOperaciones;
import cl.primerplayer.eventos.application.port.out.InscripcionRepositorio;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class InscripcionesService implements InscripcionesOperaciones, ConsultarInscripcionesUseCase {

    private final CuposOperaciones cupos;
    private final InscripcionRepositorio inscripciones;
    private final ConsultarEventosUseCase eventos;
    private final Clock clock;

    // ---------- consultas ----------

    @Override
    @Transactional(readOnly = true)
    public List<InscripcionVista> delCliente(long clienteId) {
        return inscripciones.delCliente(clienteId, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public List<InscripcionVista> delEvento(long eventoId) {
        eventos.obtenerAdmin(eventoId);   // 404 item-unavailable si no existe o fue eliminado
        return inscripciones.delEvento(eventoId, clock.instant());
    }

    // ---------- cancelación por el cliente ----------

    @Override
    @Transactional
    public InscripcionVista cancelarPorCliente(long clienteId, long inscripcionId) {
        Instant ahora = clock.instant();
        var liberado = inscripciones.cancelarConfirmada(inscripcionId, clienteId, ahora);   // UPDATE condicional
        if (liberado.isPresent()) {
            cupos.liberar(List.of(new LineaCupo(liberado.get().eventoId(), liberado.get().cantidad())));
            return inscripciones.porId(inscripcionId, ahora).orElseThrow();
        }
        throw motivoNoCancelable(clienteId, inscripcionId, ahora);
    }

    /** El UPDATE no afectó filas: se explica por qué (en este orden de prioridad). */
    private RuntimeException motivoNoCancelable(long clienteId, long inscripcionId, Instant ahora) {
        InscripcionVista i = inscripciones.porId(inscripcionId, ahora)
                .filter(x -> x.clienteId() == clienteId)   // de otro cliente: 404, no revela que existe
                .orElse(null);
        if (i == null) {
            return new RecursoNoEncontradoException("La inscripción no existe.");
        }
        return switch (i.estado()) {
            case RESERVADA -> new InscripcionNoCancelableException(
                    "La inscripción está pendiente de pago: si no completas el pago, se libera sola.");
            case CANCELADA -> new InscripcionNoCancelableException("La inscripción ya estaba cancelada.");
            case EXPIRADA -> new InscripcionNoCancelableException("La reserva venció sin pago.");
            case CONFIRMADA -> CuposInsuficientesException.pasado(0, i.eventoId());   // el evento ya comenzó
        };
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)   // la abre quien vende
    public void inscribir(long clienteId, long pedidoId, List<LineaCupo> lineas) {
        cupos.ocupar(lineas);   // atómico; si falla, nada se inserta y la transacción completa se deshace
        Instant ahora = clock.instant();
        for (LineaCupo l : lineas) {
            inscripciones.insertarReservada(l.eventoId(), clienteId, pedidoId, l.cantidad(), ahora);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmar(long pedidoId) {
        inscripciones.confirmarDelPedido(pedidoId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void liberar(long pedidoId, boolean porVencimiento) {
        var liberados = inscripciones.cerrarVigentesDelPedido(pedidoId, porVencimiento ? "EXPIRADA" : "CANCELADA",
                clock.instant());
        if (!liberados.isEmpty()) {
            cupos.liberar(liberados.stream().map(c -> new LineaCupo(c.eventoId(), c.cantidad())).toList());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public int cuposVigentes(long clienteId, long eventoId) {
        return inscripciones.cuposVigentes(clienteId, eventoId);
    }
}
