package cl.primerplayer.eventos.api;

import java.time.Instant;
import java.util.List;

/**
 * Puerto de entrada: inscripciones a eventos para "mis inscripciones" y para la lista de inscritos de la tienda.
 * No conoce pedidos ni personas: entrega ids ({@code pedidoId}, {@code clienteId}) para que quien expone la API
 * complete código de pedido y datos del cliente.
 */
public interface ConsultarInscripcionesUseCase {

    /** Inscripciones del cliente: próximas primero (por fecha), luego las pasadas (más recientes primero). */
    List<InscripcionVista> delCliente(long clienteId);

    /**
     * Inscripciones de un evento (vigentes primero, por orden de inscripción). Incluye eventos no publicados.
     *
     * @throws cl.primerplayer.shared.api.RecursoNoEncontradoException si el evento no existe o fue eliminado
     */
    List<InscripcionVista> delEvento(long eventoId);

    /**
     * El cliente cancela una inscripción CONFIRMADA (pagada) de un evento que aún no ocurre: pasa a CANCELADA y sus
     * cupos se liberan. No reembolsa el pago (decisión de negocio pendiente): eso lo gestiona la tienda.
     *
     * @throws cl.primerplayer.shared.api.RecursoNoEncontradoException si no existe o es de otro cliente
     * @throws InscripcionNoCancelableException 409 {@code registration-not-cancellable} (pendiente de pago, ya cancelada o expirada)
     * @throws CuposInsuficientesException 409 {@code event-past} si el evento ya comenzó
     */
    InscripcionVista cancelarPorCliente(long clienteId, long inscripcionId);

    enum EstadoInscripcion { RESERVADA, CONFIRMADA, CANCELADA, EXPIRADA }

    /**
     * Inscripción con el snapshot del evento al momento de la compra (título, fecha, lugar y precio).
     *
     * @param cancelable el cliente puede cancelarla ahora (CONFIRMADA y el evento aún no comienza)
     */
    record InscripcionVista(long id, long eventoId, TipoEvento tipo, long clienteId, long pedidoId, int cantidad,
                            EstadoInscripcion estado, String titulo, Instant fecha, String lugar, long precio,
                            Instant creadaEn, Instant canceladaEn, boolean cancelable) {

        public boolean vigente() {
            return estado == EstadoInscripcion.RESERVADA || estado == EstadoInscripcion.CONFIRMADA;
        }
    }
}
