package cl.primerplayer.eventos.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code pp.eventos.*}.
 *
 * <p>TODO(pregunta-abierta): confirmar con la tienda la duración estimada de torneos sin hora de término (3 h),
 * la anticipación mínima para pedir un cumpleaños (48 h) y la duración permitida del bloque (1 a 6 h).
 */
@ConfigurationProperties("pp.eventos")
public record EventosProperties(
        @DefaultValue("3h") Duration duracionEstimada,
        @DefaultValue("Av. Portales Ote. 1126, San Bernardo") String lugarPorDefecto,
        @DefaultValue Cumpleanos cumpleanos) {

    public record Cumpleanos(
            @DefaultValue("48h") Duration anticipacionMinima,
            @DefaultValue("1h") Duration duracionMinima,
            @DefaultValue("6h") Duration duracionMaxima) {}
}
