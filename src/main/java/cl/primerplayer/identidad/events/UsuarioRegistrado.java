package cl.primerplayer.identidad.events;

/**
 * Publicado al registrarse un cliente. {@code notificaciones} lo escucha para enviar el correo de verificación.
 *
 * <p>TODO(pregunta-abierta): el token de verificación viaja en claro en el evento (y quedará en la tabla
 * {@code event_publication} del outbox cuando exista un listener). Alternativa: que notificaciones pida el
 * token a identidad. Además falta la migración de {@code event_publication} antes de crear el primer listener.
 */
public record UsuarioRegistrado(long usuarioId, String email, String nombre, String tokenVerificacion) {
    @Override
    public String toString() {
        return "UsuarioRegistrado[usuarioId=" + usuarioId + "]";
    }
}
