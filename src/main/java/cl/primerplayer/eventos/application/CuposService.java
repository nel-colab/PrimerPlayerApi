package cl.primerplayer.eventos.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.eventos.api.CuposInsuficientesException;
import cl.primerplayer.eventos.api.CuposOperaciones;
import cl.primerplayer.eventos.application.port.out.CuposRepositorio;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class CuposService implements CuposOperaciones {

    private final CuposRepositorio cupos;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)   // la abre checkout o pos
    public void ocupar(List<LineaCupo> lineas) {
        Instant ahora = clock.instant();
        enOrden(lineas).forEach(i -> {
            LineaCupo l = lineas.get(i);
            if (cupos.ocupar(l.eventoId(), l.cantidad(), ahora).isEmpty()) {
                throw diagnosticar(i, l.eventoId(), ahora);
            }
        });
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void liberar(List<LineaCupo> lineas) {
        enOrden(lineas).forEach(i -> {
            LineaCupo l = lineas.get(i);
            cupos.liberar(l.eventoId(), l.cantidad()).orElseThrow(() -> new IllegalStateException(
                    "No se pueden liberar " + l.cantidad() + " cupos del evento " + l.eventoId()));
        });
    }

    /** Mismo orden de bloqueo en todas las transacciones (evita interbloqueos). */
    private static IntStream enOrden(List<LineaCupo> lineas) {
        return IntStream.range(0, lineas.size()).boxed()
                .sorted(Comparator.comparingLong(i -> lineas.get(i).eventoId()))
                .mapToInt(Integer::intValue);
    }

    private RuntimeException diagnosticar(int linea, long eventoId, Instant ahora) {
        return cupos.estado(eventoId)
                .filter(CuposRepositorio.EstadoCupos::disponibleParaVenta)
                .<RuntimeException>map(e -> e.inicio().isAfter(ahora)
                        ? CuposInsuficientesException.lleno(linea, eventoId, e.disponibles())
                        : CuposInsuficientesException.pasado(linea, eventoId))
                .orElseGet(() -> new RecursoNoEncontradoException("El evento " + eventoId + " no está disponible."));
    }
}
