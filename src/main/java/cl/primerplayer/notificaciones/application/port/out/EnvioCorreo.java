package cl.primerplayer.notificaciones.application.port.out;

/** Puerto de salida: envío de un correo (SMTP). Nunca dentro de una transacción. */
public interface EnvioCorreo {

    /** @throws RuntimeException si el servidor de correo no lo aceptó (el evento se reintentará) */
    void enviar(String para, String asunto, String texto);
}
