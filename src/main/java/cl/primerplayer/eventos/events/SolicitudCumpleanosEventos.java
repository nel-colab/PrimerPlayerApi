package cl.primerplayer.eventos.events;

import java.time.Instant;

/** Eventos de dominio de las solicitudes de cumpleaños (los escuchará {@code notificaciones} para enviar correos). */
public final class SolicitudCumpleanosEventos {

    private SolicitudCumpleanosEventos() {}

    /** Un cliente pidió un bloque: avisar a la tienda. */
    public record Recibida(long solicitudId, long clienteId, Instant inicio, Instant fin) {}

    /** La tienda aprobó o rechazó: avisar al cliente. {@code eventoId} es null si fue rechazada. */
    public record Resuelta(long solicitudId, long clienteId, boolean aprobada, Long eventoId, String motivoRechazo) {}
}
