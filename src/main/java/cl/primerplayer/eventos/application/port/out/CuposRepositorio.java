package cl.primerplayer.eventos.application.port.out;

import java.time.Instant;
import java.util.Optional;

/** Puerto de salida: UPDATE atómicos de {@code evento.cupos_ocupados}. Requieren transacción. */
public interface CuposRepositorio {

    /**
     * Ocupa si el evento está publicado, vigente, no es cumpleaños, no ha ocurrido y alcanzan los cupos.
     *
     * @return cupos disponibles después de ocupar; vacío = 0 filas afectadas
     */
    Optional<Integer> ocupar(long eventoId, int cantidad, Instant ahora);

    /** @return cupos disponibles después de liberar; vacío si no existe o liberaría más de lo ocupado */
    Optional<Integer> liberar(long eventoId, int cantidad);

    /** Para explicar por qué no se pudo ocupar. Vacío si no existe. */
    Optional<EstadoCupos> estado(long eventoId);

    record EstadoCupos(boolean disponibleParaVenta, Instant inicio, int disponibles) {}
}
