package cl.primerplayer.notificaciones.infrastructure;

import java.util.Locale;

import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import cl.primerplayer.notificaciones.application.MensajeCorreo;
import lombok.RequiredArgsConstructor;

/** HTML de un correo con la plantilla única {@code templates/correo/mensaje.html} (Thymeleaf escapa todo el texto). */
@Component
@RequiredArgsConstructor
public class RenderizadorCorreo {

    static final String PLANTILLA = "correo/mensaje";
    /** Content-ID de los logos incrustados ({@code <img src="cid:...">} en la plantilla). */
    static final String CID_LOGO = "logo-pp";
    static final String CID_LETRAS = "logo-letras";

    private final ITemplateEngine plantillas;

    public String html(MensajeCorreo mensaje) {
        Context ctx = new Context(Locale.of("es", "CL"));
        ctx.setVariable("m", mensaje);
        return plantillas.process(PLANTILLA, ctx);
    }
}
