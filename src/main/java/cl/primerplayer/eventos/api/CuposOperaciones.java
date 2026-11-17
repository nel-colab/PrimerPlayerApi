package cl.primerplayer.eventos.api;

import java.util.List;

/**
 * Puerto de entrada para {@code checkout} y {@code pos}: ocupa y libera cupos de forma atómica. Ambos métodos
 * exigen una transacción abierta por quien orquesta (propagation MANDATORY).
 */
public interface CuposOperaciones {

    /**
     * {@code UPDATE ... WHERE cupos_ocupados + q <= cupos_totales AND fecha_hora > ahora}, en orden de eventoId.
     *
     * @throws CuposInsuficientesException {@code event-full} o {@code event-past}, con la línea en {@code errors[]}
     * @throws cl.primerplayer.shared.api.RecursoNoEncontradoException no existe, no publicado, eliminado o cumpleaños
     */
    void ocupar(List<LineaCupo> lineas);

    /** Devuelve cupos (cancelación, pago rechazado, reserva expirada). */
    void liberar(List<LineaCupo> lineas);

    record LineaCupo(long eventoId, int cantidad) {
        public LineaCupo {
            if (cantidad < 1) throw new IllegalArgumentException("cantidad debe ser >= 1");
        }
    }
}
