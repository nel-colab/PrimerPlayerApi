package cl.primerplayer.identidad.events;

import java.util.UUID;

/**
 * Publicado al registrarse un cliente. {@code notificaciones} lo escucha para enviar el correo de verificación.
 * <p>No lleva el token: el evento queda guardado en el outbox ({@code event_publication}) hasta enviarse, y un token
 * en claro ahí (o en un respaldo de la base) serviría para verificar la cuenta. Notificaciones lo pide a
 * {@link cl.primerplayer.identidad.api.EnlacesCuentaUseCase} justo antes de enviar.
 *
 * @param solicitudId identifica esta solicitud: el correo se envía una sola vez aunque el outbox reintente
 */
public record UsuarioRegistrado(long usuarioId, String email, String nombre, UUID solicitudId) {}
