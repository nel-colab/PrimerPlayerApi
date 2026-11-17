package cl.primerplayer.eventos.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import cl.primerplayer.eventos.api.SolicitudNoPendienteException;
import cl.primerplayer.eventos.api.SolicitudesCumpleanosUseCase.EstadoSolicitud;
import cl.primerplayer.shared.api.ValidacionException;

/** Solicitud de cumpleaños: PENDIENTE → APROBADA | RECHAZADA | CANCELADA. Modelo puro e inmutable. */
public record SolicitudCumpleanos(
        Long id,
        long clienteId,
        Bloque bloque,
        String festejado,
        int invitados,
        String telefono,
        String comentario,
        EstadoSolicitud estado,
        String motivoRechazo,
        Long eventoId,
        Long resueltaPor,
        Instant resueltaEn,
        Instant creadoEn) {

    /** Reglas configurables del bloque solicitado. */
    public record Reglas(Duration anticipacionMinima, Duration duracionMinima, Duration duracionMaxima) {}

    public static SolicitudCumpleanos nueva(long clienteId, Instant inicio, Instant fin, String festejado,
                                            int invitados, String telefono, String comentario, Reglas reglas,
                                            Instant ahora) {
        if (inicio == null || fin == null || !fin.isAfter(inicio)) {
            throw new ValidacionException("fin", "La hora de término debe ser posterior a la de inicio.");
        }
        if (inicio.isBefore(ahora.plus(reglas.anticipacionMinima()))) {
            throw new ValidacionException("inicio", "Se debe solicitar con al menos "
                    + reglas.anticipacionMinima().toHours() + " horas de anticipación.");
        }
        Bloque bloque = new Bloque(inicio, fin);
        if (bloque.duracion().compareTo(reglas.duracionMinima()) < 0
                || bloque.duracion().compareTo(reglas.duracionMaxima()) > 0) {
            throw new ValidacionException("fin", "El bloque debe durar entre " + reglas.duracionMinima().toHours()
                    + " y " + reglas.duracionMaxima().toHours() + " horas.");
        }
        return new SolicitudCumpleanos(null, clienteId, bloque, festejado.strip(), invitados, telefono.strip(),
                comentario == null || comentario.isBlank() ? null : comentario.strip(), EstadoSolicitud.PENDIENTE,
                null, null, null, null, ahora);
    }

    public SolicitudCumpleanos aprobar(long eventoId, long usuarioId, Instant ahora) {
        exigirPendiente();
        return conEstado(EstadoSolicitud.APROBADA, null, eventoId, usuarioId, ahora);
    }

    public SolicitudCumpleanos rechazar(String motivo, long usuarioId, Instant ahora) {
        exigirPendiente();
        if (motivo == null || motivo.isBlank()) {
            throw new ValidacionException("motivo", "Indica el motivo del rechazo.");
        }
        return conEstado(EstadoSolicitud.RECHAZADA, motivo.strip(), null, usuarioId, ahora);
    }

    /**
     * El cliente solo cancela mientras está pendiente.
     * TODO(pregunta-abierta): ¿puede cancelar una ya aprobada? (liberaría el bloque y eliminaría el evento).
     */
    public SolicitudCumpleanos cancelar(Instant ahora) {
        exigirPendiente();
        return conEstado(EstadoSolicitud.CANCELADA, null, null, null, ahora);
    }

    private void exigirPendiente() {
        if (estado != EstadoSolicitud.PENDIENTE) {
            throw new SolicitudNoPendienteException(estado.name());
        }
    }

    private SolicitudCumpleanos conEstado(EstadoSolicitud nuevo, String motivo, Long evento, Long por, Instant ahora) {
        return new SolicitudCumpleanos(id, clienteId, bloque, festejado, invitados, telefono, comentario, nuevo,
                motivo, evento, por, ahora, creadoEn);
    }

    public SolicitudCumpleanos {
        Objects.requireNonNull(bloque);
        Objects.requireNonNull(estado);
    }
}
