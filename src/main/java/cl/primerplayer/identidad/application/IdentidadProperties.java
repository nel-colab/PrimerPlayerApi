package cl.primerplayer.identidad.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Políticas de autenticación ({@code pp.auth.*}).
 *
 * <p>TODO(pregunta-abierta): los valores por defecto (refresh 30 días, verificación 24 h, 5 intentos y 15 min de
 * bloqueo, 10 s de gracia en la rotación) no están en el documento de arquitectura disponible; confirmar con
 * negocio/seguridad.
 *
 * @param refreshGracia ventana en que un refresh ya rotado responde 401 sin revocar la familia: cubre dos pestañas
 *                      que refrescan a la vez (la perdedora reintenta y el navegador ya envía la cookie nueva)
 */
@ConfigurationProperties("pp.auth")
public record IdentidadProperties(
        @DefaultValue("30d") Duration refreshTtl,
        @DefaultValue("24h") Duration verificacionTtl,
        @DefaultValue Bloqueo bloqueo,
        @DefaultValue("10s") Duration refreshGracia) {

    public record Bloqueo(@DefaultValue("5") int maxIntentos, @DefaultValue("15m") Duration duracion) {}
}
