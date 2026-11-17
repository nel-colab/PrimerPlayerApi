package cl.primerplayer.ventas.api;

import java.util.List;

import cl.primerplayer.eventos.api.ConsultarInscripcionesUseCase.InscripcionVista;

/**
 * Inscripciones a eventos vistas desde la venta: junta los datos del módulo eventos con el código del pedido (ventas)
 * y los datos del cliente (identidad). Así eventos no depende de ventas ni de identidad.
 */
public interface InscripcionesVentaUseCase {

    /** "Mis inscripciones": próximas primero y luego las pasadas. */
    List<InscripcionCliente> delCliente(long clienteId);

    /**
     * Cancela una inscripción pagada de un evento futuro y libera sus cupos. No reembolsa (pendiente de negocio).
     *
     * @throws cl.primerplayer.shared.api.RecursoNoEncontradoException 404 si no existe o es de otro cliente
     * @throws cl.primerplayer.eventos.api.InscripcionNoCancelableException 409 {@code registration-not-cancellable}
     * @throws cl.primerplayer.eventos.api.CuposInsuficientesException 409 {@code event-past}
     */
    InscripcionCliente cancelar(long clienteId, long inscripcionId);

    /**
     * Inscritos de un evento para la tienda (incluye cancelados y expirados al final).
     *
     * @throws cl.primerplayer.shared.api.RecursoNoEncontradoException 404 si el evento no existe o fue eliminado
     */
    InscritosEvento inscritos(long eventoId);

    record InscripcionCliente(InscripcionVista inscripcion, String codigoPedido) {}

    record Inscrito(InscripcionVista inscripcion, String codigoPedido, CanalPedido canal, String clienteNombre,
                    String clienteEmail) {}

    /** @param cuposConfirmados / cuposReservados suma de {@code cantidad} de las inscripciones en ese estado */
    record InscritosEvento(long eventoId, int cuposConfirmados, int cuposReservados, List<Inscrito> inscritos) {}
}
