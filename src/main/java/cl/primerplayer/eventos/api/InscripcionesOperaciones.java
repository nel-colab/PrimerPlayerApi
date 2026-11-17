package cl.primerplayer.eventos.api;

import java.util.List;

import cl.primerplayer.eventos.api.CuposOperaciones.LineaCupo;

/**
 * Puerto de entrada para quien vende cupos ({@code ventas} en el checkout web, {@code pos} a futuro): inscripciones
 * ligadas a un pedido. Los comandos exigen una transacción abierta por quien orquesta (propagation MANDATORY):
 * cupos, inscripciones, stock y pedido se confirman o se deshacen juntos.
 */
public interface InscripcionesOperaciones {

    /**
     * Ocupa los cupos (UPDATE atómico, en orden de eventoId) y crea una inscripción {@code RESERVADA} por línea, con
     * snapshot de título, fecha, lugar y precio del evento.
     *
     * @throws CuposInsuficientesException {@code event-full} / {@code event-past}; {@code errors[].line} es el índice en {@code lineas}
     * @throws cl.primerplayer.shared.api.RecursoNoEncontradoException evento no publicado, eliminado o cumpleaños
     */
    void inscribir(long clienteId, long pedidoId, List<LineaCupo> lineas);

    /** Pago confirmado: las inscripciones {@code RESERVADA} del pedido pasan a {@code CONFIRMADA}. */
    void confirmar(long pedidoId);

    /**
     * Pedido sin pago (rechazado, anulado o vencido): las inscripciones vigentes pasan a {@code EXPIRADA}
     * (si {@code porVencimiento}) o {@code CANCELADA}, y sus cupos se liberan.
     */
    void liberar(long pedidoId, boolean porVencimiento);

    /** Cupos que el cliente ya tiene en el evento (inscripciones RESERVADA + CONFIRMADA), para el límite por cliente. */
    int cuposVigentes(long clienteId, long eventoId);
}
