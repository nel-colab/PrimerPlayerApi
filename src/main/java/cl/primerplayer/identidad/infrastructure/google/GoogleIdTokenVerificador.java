package cl.primerplayer.identidad.infrastructure.google;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import cl.primerplayer.identidad.api.GoogleNoDisponibleException;
import cl.primerplayer.identidad.api.GoogleTokenInvalidoException;
import cl.primerplayer.identidad.application.port.out.VerificadorGoogle;
import lombok.extern.slf4j.Slf4j;

/**
 * Valida el ID token de Google Identity Services como lo indica Google: firma RS256 con sus llaves públicas (JWKS,
 * que Nimbus cachea), {@code iss} de Google, {@code aud} = nuestro client id y vigencia. No hace falta el SDK de
 * Google: el decodificador de Spring Security cubre todo eso.
 * <p>No se registra como bean {@link JwtDecoder}: el resource server sigue validando solo el JWT propio de la tienda.
 */
@Component
@Slf4j
class GoogleIdTokenVerificador implements VerificadorGoogle {

    static final String JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    static final Set<String> EMISORES = Set.of("https://accounts.google.com", "accounts.google.com");

    private final JwtDecoder decoder;
    private final String clientId;

    @Autowired
    GoogleIdTokenVerificador(GoogleProperties props, Clock clock) {
        this(props.clientId(), id -> {
            NimbusJwtDecoder d = NimbusJwtDecoder.withJwkSetUri(JWKS).jwsAlgorithm(SignatureAlgorithm.RS256).build();
            d.setJwtValidator(validador(id, clock));
            return d;
        });
    }

    /** @param fabrica crea el decodificador para el client id (las pruebas entregan uno sin red) */
    GoogleIdTokenVerificador(String clientId, Function<String, JwtDecoder> fabrica) {
        this.clientId = clientId == null ? "" : clientId.strip();
        if (this.clientId.isEmpty()) {
            this.decoder = null;
            log.warn("Inicio de sesión con Google deshabilitado: falta pp.auth.google.client-id (PP_AUTH_GOOGLE_CLIENT_ID)");
            return;
        }
        this.decoder = fabrica.apply(this.clientId);
    }

    /** Vigencia (con el reloj inyectable), emisor de Google y audiencia = nuestro client id. */
    static OAuth2TokenValidator<Jwt> validador(String clientId, Clock clock) {
        JwtTimestampValidator vigencia = new JwtTimestampValidator();
        vigencia.setClock(clock);
        return new DelegatingOAuth2TokenValidator<>(vigencia, emisor(), audiencia(clientId));
    }

    @Override
    public java.util.Optional<String> clientId() {
        return clientId.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(clientId);
    }

    @Override
    public IdentidadGoogle verificar(String idToken) {
        if (decoder == null) {
            throw new GoogleNoDisponibleException();
        }
        if (idToken == null || idToken.isBlank()) {
            throw new GoogleTokenInvalidoException();
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken.strip());
        } catch (BadJwtException e) {   // firma, formato, emisor, audiencia o vigencia
            log.info("ID token de Google rechazado: {}", e.getMessage());
            throw new GoogleTokenInvalidoException();
        } catch (JwtException e) {      // no se pudieron obtener las llaves públicas de Google
            log.warn("No se pudo validar el ID token de Google: {}", e.getMessage());
            throw new GoogleNoDisponibleException();
        }
        String email = jwt.getClaimAsString("email");
        if (jwt.getSubject() == null || email == null || email.isBlank()) {
            throw new GoogleTokenInvalidoException();
        }
        Object verificado = jwt.getClaims().get("email_verified");   // boolean (o "true" en tokens antiguos)
        boolean emailVerificado = Boolean.TRUE.equals(verificado) || "true".equals(verificado);
        return new IdentidadGoogle(jwt.getSubject(), email, emailVerificado, jwt.getClaimAsString("name"));
    }

    private static OAuth2TokenValidator<Jwt> emisor() {
        return jwt -> EMISORES.contains(jwt.getClaimAsString("iss"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "El emisor no es Google", null));
    }

    private static OAuth2TokenValidator<Jwt> audiencia(String clientId) {
        return jwt -> {
            List<String> aud = jwt.getAudience();
            return aud != null && aud.contains(clientId)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token de otra aplicación", null));
        };
    }
}
