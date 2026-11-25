package cl.primerplayer.notificaciones;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import cl.primerplayer.notificaciones.application.MensajeCorreo;
import cl.primerplayer.notificaciones.application.port.out.EnvioCorreo;

/** Correo simulado: guarda lo que se habría enviado. */
public class CorreosCapturados implements EnvioCorreo {

    /** @param asunto asunto completo (con " · Primer Player"); {@code texto} es la versión de texto plano */
    public record Correo(String para, String asunto, String texto, MensajeCorreo mensaje) {}

    public final List<Correo> enviados = new CopyOnWriteArrayList<>();

    @Override
    public void enviar(String para, MensajeCorreo mensaje) {
        enviados.add(new Correo(para, mensaje.asuntoCompleto(), mensaje.textoPlano(), mensaje));
    }

    public List<Correo> para(String email) {
        return enviados.stream().filter(c -> c.para().equalsIgnoreCase(email)).toList();
    }

    /** Código de 6 dígitos del último correo a ese destino con ese texto en el asunto (el bloque destacado). */
    public String codigo(String email, String asunto) {
        Correo c = para(email).stream().filter(x -> x.asunto().contains(asunto)).reduce((a, b) -> b).orElseThrow();
        if (c.mensaje().destacado() == null || !c.mensaje().destacado().valor().matches("\\d{6}")) {
            throw new AssertionError("El correo no trae un código de 6 dígitos: " + c.texto());
        }
        return c.mensaje().destacado().valor();
    }

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        CorreosCapturados correosCapturados() {
            return new CorreosCapturados();
        }
    }
}
