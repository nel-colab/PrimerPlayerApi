package cl.primerplayer.shared.infrastructure;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RelojConfig {

    /** Zona de los cortes diarios (caja, reportes). Las fechas se guardan siempre en UTC. */
    public static final ZoneId ZONA_TIENDA = ZoneId.of("America/Santiago");

    /** Única fuente de "ahora" de la aplicación: las pruebas lo reemplazan por un reloj fijo. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
