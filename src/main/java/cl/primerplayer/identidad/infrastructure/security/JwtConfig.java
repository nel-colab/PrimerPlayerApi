package cl.primerplayer.identidad.infrastructure.security;

import java.time.Clock;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/** JWT propio firmado con HMAC-SHA256 (un solo servicio emite y valida). */
@Configuration(proxyBeanMethods = false)
class JwtConfig {

    @Bean
    SecretKey jwtSecretKey(JwtProperties props) {
        if (props.secreto() == null || props.secreto().isBlank()) {
            throw new IllegalStateException("Falta pp.auth.jwt.secreto (variable PP_AUTH_JWT_SECRETO)");
        }
        byte[] bytes = Base64.getDecoder().decode(props.secreto());
        if (bytes.length < 32) {
            throw new IllegalStateException("pp.auth.jwt.secreto debe tener al menos 256 bits");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey, JwtProperties props, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator expiracion = new JwtTimestampValidator();
        expiracion.setClock(clock);   // el mismo reloj que emite: las pruebas pueden fijarlo
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                expiracion, new JwtIssuerValidator(props.emisor())));
        return decoder;
    }
}
