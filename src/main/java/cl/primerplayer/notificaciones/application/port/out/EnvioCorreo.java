package cl.primerplayer.notificaciones.application.port.out;

import cl.primerplayer.notificaciones.application.MensajeCorreo;

/** Puerto de salida: envío de un correo (HTML con la marca + texto plano). Nunca dentro de una transacción. */
public interface EnvioCorreo {

    /** @throws RuntimeException si el servidor de correo no lo aceptó (el evento se reintentará) */
    void enviar(String para, MensajeCorreo mensaje);
}
