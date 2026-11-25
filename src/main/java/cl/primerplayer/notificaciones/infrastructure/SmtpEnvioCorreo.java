package cl.primerplayer.notificaciones.infrastructure;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import cl.primerplayer.notificaciones.application.MensajeCorreo;
import cl.primerplayer.notificaciones.application.port.out.EnvioCorreo;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * Envío por SMTP ({@code spring.mail.*}; Mailpit en local: http://localhost:8025). Cada correo es
 * multipart/alternative (texto plano + HTML con la marca) con los dos logos incrustados como imágenes inline
 * ({@code cid:}): no se cargan desde ninguna URL ni son enlaces. Si no hay servidor configurado, solo deja un aviso
 * en el log (sin el contenido: puede traer códigos de verificación).
 */
@Component
@Slf4j
class SmtpEnvioCorreo implements EnvioCorreo {

    private static final ClassPathResource LOGO = new ClassPathResource("correo/logo-pp.png");
    private static final ClassPathResource LETRAS = new ClassPathResource("correo/logo-letras.png");

    private final ObjectProvider<JavaMailSender> mailSender;
    private final RenderizadorCorreo renderizador;
    private final String remitente;

    SmtpEnvioCorreo(ObjectProvider<JavaMailSender> mailSender, RenderizadorCorreo renderizador,
                    @Value("${pp.notificaciones.remitente:Primer Player <no-responder@primerplayer.cl>}") String remitente) {
        this.mailSender = mailSender;
        this.renderizador = renderizador;
        this.remitente = remitente;
    }

    @Override
    public void enviar(String para, MensajeCorreo mensaje) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Sin servidor de correo configurado (spring.mail.host): no se envía «{}»", mensaje.asunto());
            return;
        }
        MimeMessage m = sender.createMimeMessage();
        try {
            // MULTIPART_MODE_MIXED_RELATED: alternativa texto/HTML + imágenes relacionadas con el HTML (inline)
            MimeMessageHelper h = new MimeMessageHelper(m, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());
            h.setFrom(remitente);
            h.setTo(para);
            h.setSubject(mensaje.asuntoCompleto());
            h.setText(mensaje.textoPlano(), renderizador.html(mensaje));
            // Los inline van después de setText (lo exige MimeMessageHelper)
            h.addInline(RenderizadorCorreo.CID_LOGO, LOGO, "image/png");
            h.addInline(RenderizadorCorreo.CID_LETRAS, LETRAS, "image/png");
        } catch (MessagingException e) {
            throw new MailPreparationException("No se pudo armar el correo «" + mensaje.asunto() + "»", e);
        }
        sender.send(m);
    }
}
