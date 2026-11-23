package cl.primerplayer.identidad.events;

import java.util.UUID;

/**
 * Se pidió restablecer la contraseña (el propio usuario o un administrador). Lo escucha notificaciones para enviar el
 * enlace. Sin token, igual que {@link UsuarioRegistrado}: el enlace se emite al enviar el correo.
 *
 * @param solicitudId identifica esta solicitud: el correo se envía una sola vez aunque el outbox reintente
 */
public record RecuperacionSolicitada(long usuarioId, String email, String nombre, UUID solicitudId) {}
