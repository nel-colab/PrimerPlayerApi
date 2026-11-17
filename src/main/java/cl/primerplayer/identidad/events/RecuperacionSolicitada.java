package cl.primerplayer.identidad.events;

/**
 * Se pidió restablecer la contraseña (el propio usuario o un administrador). Lo escucha notificaciones para enviar el
 * enlace. El token viaja en el evento y el outbox lo borra al completarse ({@code completion-mode: delete}).
 */
public record RecuperacionSolicitada(long usuarioId, String email, String nombre, String tokenRecuperacion) {
    @Override
    public String toString() {
        return "RecuperacionSolicitada[usuarioId=" + usuarioId + "]";
    }
}
