package cl.primerplayer.identidad.events;

/** El usuario (o un administrador) pidió reenviar el correo de verificación. Lo escucha notificaciones. */
public record VerificacionSolicitada(long usuarioId, String email, String nombre, String tokenVerificacion) {
    @Override
    public String toString() {
        return "VerificacionSolicitada[usuarioId=" + usuarioId + "]";   // nunca el token en los logs
    }
}
