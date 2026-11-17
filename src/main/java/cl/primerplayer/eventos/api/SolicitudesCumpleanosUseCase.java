package cl.primerplayer.eventos.api;

import java.time.Instant;
import java.util.List;

import cl.primerplayer.shared.api.Pagina;

/** Puerto de entrada: solicitudes de cumpleaños (cliente solicita; la tienda aprueba o rechaza). */
public interface SolicitudesCumpleanosUseCase {

    // ---------- cliente ----------

    /**
     * Crea la solicitud en estado PENDIENTE.
     *
     * @throws BloqueOcupadoException si el bloque ya está tomado por un evento o cumpleaños aprobado
     */
    SolicitudVista solicitar(long clienteId, NuevaSolicitud solicitud);

    List<SolicitudVista> deCliente(long clienteId);

    /** Solo las propias y PENDIENTE. Una ajena responde 404 (no revela existencia). */
    SolicitudVista cancelar(long clienteId, long solicitudId);

    // ---------- tienda ----------

    Pagina<SolicitudVista> buscar(EstadoSolicitud estado, int page, int size);

    /**
     * Aprueba y crea el evento de cumpleaños que reserva el bloque (se publica en la agenda).
     *
     * @throws SolicitudNoPendienteException
     * @throws BloqueOcupadoException si el bloque ya no está libre
     */
    SolicitudVista aprobar(long solicitudId, Aprobacion aprobacion, long usuarioId);

    /** @throws SolicitudNoPendienteException */
    SolicitudVista rechazar(long solicitudId, String motivo, long usuarioId);

    record NuevaSolicitud(Instant inicio, Instant fin, String festejado, int invitados, String telefono,
                          String comentario) {}

    /** Textos opcionales del evento público; si vienen vacíos se usan los por defecto ("Tienda cerrada..."). */
    record Aprobacion(String titulo, String descripcion, List<String> consideraciones, Boolean publicado) {}

    enum EstadoSolicitud { PENDIENTE, APROBADA, RECHAZADA, CANCELADA }

    record SolicitudVista(long id, long clienteId, Instant inicio, Instant fin, String festejado, int invitados,
                          String telefono, String comentario, EstadoSolicitud estado, String motivoRechazo,
                          Long eventoId, Instant resueltaEn, Instant creadoEn) {}
}
