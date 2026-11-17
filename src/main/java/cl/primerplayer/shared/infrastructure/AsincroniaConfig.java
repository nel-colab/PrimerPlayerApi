package cl.primerplayer.shared.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Habilita {@code @Async}: los escuchas de eventos que hablan con servicios externos (correo) corren fuera del hilo y
 * de la transacción de quien publica.
 */
@Configuration
@EnableAsync
class AsincroniaConfig {
}
