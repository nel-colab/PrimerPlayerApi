package cl.primerplayer.notificaciones.infrastructure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import cl.primerplayer.notificaciones.application.port.out.EnvioCorreo;
import lombok.extern.slf4j.Slf4j;

/**
 * Envío por SMTP ({@code spring.mail.*}; Mailpit en local: http://localhost:8025). Si no hay servidor configurado,
 * solo deja un aviso en el log (sin el contenido: puede traer enlaces con tokens).
 */
@Component
@Slf4j
class SmtpEnvioCorreo implements EnvioCorreo {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String remitente;

    SmtpEnvioCorreo(ObjectProvider<JavaMailSender> mailSender,
                    @Value("${pp.notificaciones.remitente:Primer Player <no-responder@primerplayer.cl>}") String remitente) {
        this.mailSender = mailSender;
        this.remitente = remitente;
    }

    @Override
    public void enviar(String para, String asunto, String texto) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Sin servidor de correo configurado (spring.mail.host): no se envía «{}»", asunto);
            return;
        }
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(remitente);
        m.setTo(para);
        m.setSubject(asunto);
        m.setText(texto);
        sender.send(m);
    }
}
