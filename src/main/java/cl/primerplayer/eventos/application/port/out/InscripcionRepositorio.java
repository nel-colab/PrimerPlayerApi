package cl.primerplayer.eventos.application.port.out;

import java.time.Instant;
import java.util.List;

/** Puerto de salida: tabla {@code inscripcion}. Requiere transacción. */
public interface InscripcionRepositorio {

    /** Inserta una inscripción RESERVADA con el snapshot del evento leído en la misma sentencia. */
    void insertarReservada(long eventoId, long clienteId, long pedidoId, int cantidad, Instant ahora);

    /** @return filas actualizadas */
    int confirmarDelPedido(long pedidoId);

    /**
     * Pasa las vigentes (RESERVADA/CONFIRMADA) del pedido a {@code estado} y devuelve qué cupos liberar.
     *
     * @param estado {@code CANCELADA} o {@code EXPIRADA}
     */
    List<CupoLiberado> cerrarVigentesDelPedido(long pedidoId, String estado, Instant ahora);

    int cuposVigentes(long clienteId, long eventoId);

    List<cl.primerplayer.eventos.api.ConsultarInscripcionesUseCase.InscripcionVista> delCliente(long clienteId, Instant ahora);

    List<cl.primerplayer.eventos.api.ConsultarInscripcionesUseCase.InscripcionVista> delEvento(long eventoId, Instant ahora);

    java.util.Optional<cl.primerplayer.eventos.api.ConsultarInscripcionesUseCase.InscripcionVista> porId(long id, Instant ahora);

    /**
     * CONFIRMADA → CANCELADA solo si es del cliente y el evento aún no comienza (UPDATE condicional atómico).
     *
     * @return el cupo a liberar; vacío si no se cumplió alguna condición
     */
    java.util.Optional<CupoLiberado> cancelarConfirmada(long inscripcionId, long clienteId, Instant ahora);

    record CupoLiberado(long eventoId, int cantidad) {}
}
