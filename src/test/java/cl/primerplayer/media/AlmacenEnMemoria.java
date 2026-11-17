package cl.primerplayer.media;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import cl.primerplayer.media.api.AlmacenNoDisponibleException;
import cl.primerplayer.media.application.port.out.AlmacenObjetos;

/** S3 simulado para las pruebas. */
public class AlmacenEnMemoria implements AlmacenObjetos {

    public final Map<String, byte[]> objetos = new ConcurrentHashMap<>();
    public volatile boolean caido;

    @Override
    public void guardar(String bucket, String clave, byte[] contenido, String mime) {
        if (caido) {
            throw new AlmacenNoDisponibleException();
        }
        objetos.put(bucket + "/" + clave, contenido);
    }

    @Override
    public String urlPublica(String bucket, String clave) {
        return "https://media.test/" + bucket + "/" + clave;
    }

    @Override
    public String urlFirmada(String bucket, String clave, Duration vigencia) {
        return "https://media.test/firmado/" + bucket + "/" + clave + "?expira=" + vigencia.toSeconds();
    }

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        AlmacenEnMemoria almacenEnMemoria() {
            return new AlmacenEnMemoria();
        }
    }
}
