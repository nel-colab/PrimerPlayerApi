package cl.primerplayer.notificaciones;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import cl.primerplayer.notificaciones.application.port.out.EnvioCorreo;

/** Correo simulado: guarda lo que se habría enviado. */
public class CorreosCapturados implements EnvioCorreo {

    public record Correo(String para, String asunto, String texto) {}

    public final List<Correo> enviados = new CopyOnWriteArrayList<>();

    @Override
    public void enviar(String para, String asunto, String texto) {
        enviados.add(new Correo(para, asunto, texto));
    }

    public List<Correo> para(String email) {
        return enviados.stream().filter(c -> c.para().equalsIgnoreCase(email)).toList();
    }

    /** Extrae el token del enlace (…?token=XYZ) del último correo a ese destino con ese texto en el asunto. */
    public String token(String email, String asunto) {
        Correo c = para(email).stream().filter(x -> x.asunto().contains(asunto)).reduce((a, b) -> b).orElseThrow();
        Matcher m = Pattern.compile("token=([A-Za-z0-9_\\-]+)").matcher(c.texto());
        if (!m.find()) {
            throw new AssertionError("El correo no trae token: " + c.texto());
        }
        return m.group(1);
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
