package cl.primerplayer.identidad.infrastructure.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code pp.auth.jwt.*}. El secreto (Base64, mínimo 256 bits) llega por variable de entorno en producción
 * ({@code PP_AUTH_JWT_SECRETO}); nunca se versiona un secreto real.
 */
@ConfigurationProperties("pp.auth.jwt")
public record JwtProperties(
        String secreto,
        @DefaultValue("primer-player-api") String emisor,
        @DefaultValue("15m") Duration ttl) {

    @Override
    public String toString() {
        return "JwtProperties[emisor=" + emisor + ", ttl=" + ttl + "]";
    }
}
