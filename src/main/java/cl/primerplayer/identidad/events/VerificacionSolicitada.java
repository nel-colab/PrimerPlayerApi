package cl.primerplayer.identidad.events;

import java.util.UUID;

/**
 * El usuario (o un administrador) pidió reenviar el correo de verificación. Lo escucha notificaciones.
 * Sin token, igual que {@link UsuarioRegistrado}.
 *
 * @param solicitudId identifica esta solicitud: el correo se envía una sola vez aunque el outbox reintente
 */
public record VerificacionSolicitada(long usuarioId, String email, String nombre, UUID solicitudId) {}
