package cl.primerplayer.identidad.infrastructure.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import cl.primerplayer.identidad.api.GoogleNoDisponibleException;
import cl.primerplayer.identidad.api.GoogleTokenInvalidoException;
import cl.primerplayer.identidad.application.port.out.VerificadorGoogle.IdentidadGoogle;

class GoogleIdTokenVerificadorTest {

    static final String CLIENT_ID = "123.apps.googleusercontent.com";
    static final Instant AHORA = Instant.parse("2026-11-26T15:00:00Z");
    static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    static Jwt jwt(Consumer<Jwt.Builder> ajuste) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256")
                .issuer("https://accounts.google.com").audience(List.of(CLIENT_ID)).subject("sub-1")
                .claim("email", "ana@gmail.com").claim("email_verified", true).claim("name", "Ana")
                .issuedAt(AHORA.minusSeconds(60)).expiresAt(AHORA.plusSeconds(3000));
        ajuste.accept(b);
        return b.build();
    }

    static GoogleIdTokenVerificador con(JwtDecoder decoder) {
        return new GoogleIdTokenVerificador(CLIENT_ID, id -> decoder);
    }

    // ---------- configuración ----------

    @Test
    void sinClientId_googleUnavailable() {
        var v = new GoogleIdTokenVerificador(new GoogleProperties(" "), Clock.systemUTC());
        assertThat(v.clientId()).isEmpty();
        assertThatThrownBy(() -> v.verificar("x.y.z")).isInstanceOf(GoogleNoDisponibleException.class);
    }

    @Test
    void clientIdNuloEquivaleASinClientId() {
        var v = new GoogleIdTokenVerificador((String) null, id -> { throw new AssertionError("no debe crearse"); });
        assertThat(v.clientId()).isEmpty();
    }

    @Test
    void clientIdSeRecortaYSeExpone() {
        var v = new GoogleIdTokenVerificador(new GoogleProperties("  " + CLIENT_ID + " "), Clock.systemUTC());
        assertThat(v.clientId()).contains(CLIENT_ID);
    }

    @Test
    void tokenVacioOMalformado_invalidGoogleToken_sinLlamarAGoogle() {
        var v = new GoogleIdTokenVerificador(new GoogleProperties(CLIENT_ID), Clock.systemUTC());
        assertThatThrownBy(() -> v.verificar(null)).isInstanceOf(GoogleTokenInvalidoException.class);
        assertThatThrownBy(() -> v.verificar(" ")).isInstanceOf(GoogleTokenInvalidoException.class);
        assertThatThrownBy(() -> v.verificar("no-es-un-jwt")).isInstanceOf(GoogleTokenInvalidoException.class);
    }

    // ---------- verificar ----------

    @Test
    void tokenValidoDevuelveLaIdentidad() {
        var v = con(t -> jwt(b -> {}));

        IdentidadGoogle id = v.verificar("  token  ");

        assertThat(id).isEqualTo(new IdentidadGoogle("sub-1", "ana@gmail.com", true, "Ana"));
    }

    @Test
    void emailVerifiedComoTextoTambienCuenta() {
        var v = con(t -> jwt(b -> b.claim("email_verified", "true")));
        assertThat(v.verificar("t").emailVerificado()).isTrue();
    }

    @Test
    void emailNoVerificadoOAusente() {
        assertThat(con(t -> jwt(b -> b.claim("email_verified", false))).verificar("t").emailVerificado()).isFalse();
        assertThat(con(t -> jwt(b -> b.claims(c -> c.remove("email_verified")))).verificar("t").emailVerificado())
                .isFalse();
    }

    @Test
    void sinEmailOSinSubjectEsInvalido() {
        assertThatThrownBy(() -> con(t -> jwt(b -> b.claims(c -> c.remove("email")))).verificar("t"))
                .isInstanceOf(GoogleTokenInvalidoException.class);
        assertThatThrownBy(() -> con(t -> jwt(b -> b.claim("email", " "))).verificar("t"))
                .isInstanceOf(GoogleTokenInvalidoException.class);
        assertThatThrownBy(() -> con(t -> jwt(b -> b.claims(c -> c.remove("sub")))).verificar("t"))
                .isInstanceOf(GoogleTokenInvalidoException.class);
    }

    @Test
    void firmaOClaimsRechazadosEsInvalido() {
        var v = con(t -> { throw new BadJwtException("firma inválida"); });
        assertThatThrownBy(() -> v.verificar("t")).isInstanceOf(GoogleTokenInvalidoException.class);
    }

    @Test
    void sinLlavesPublicasDeGoogleEsNoDisponible() {
        var v = con(t -> { throw new JwtException("JWKS inaccesible"); });
        assertThatThrownBy(() -> v.verificar("t")).isInstanceOf(GoogleNoDisponibleException.class);
    }

    // ---------- validador ----------

    @Test
    void validadorAceptaTokenDeGoogleParaNuestraApp() {
        var validador = GoogleIdTokenVerificador.validador(CLIENT_ID, RELOJ);
        assertThat(validador.validate(jwt(b -> {})).hasErrors()).isFalse();
        assertThat(validador.validate(jwt(b -> b.issuer("accounts.google.com"))).hasErrors()).isFalse();
    }

    @Test
    void validadorRechazaOtroEmisor() {
        var r = GoogleIdTokenVerificador.validador(CLIENT_ID, RELOJ).validate(jwt(b -> b.issuer("https://evil.example")));
        assertThat(r.getErrors()).anyMatch(e -> e.getDescription().contains("emisor"));
    }

    @Test
    void validadorRechazaOtraAudienciaOSinAudiencia() {
        var validador = GoogleIdTokenVerificador.validador(CLIENT_ID, RELOJ);
        assertThat(validador.validate(jwt(b -> b.audience(List.of("otra-app")))).getErrors())
                .anyMatch(e -> e.getDescription().contains("otra aplicación"));
        assertThat(validador.validate(jwt(b -> b.claims(c -> c.remove("aud")))).hasErrors()).isTrue();
    }

    @Test
    void validadorRechazaTokenVencidoSegunElReloj() {
        var r = GoogleIdTokenVerificador.validador(CLIENT_ID, RELOJ)
                .validate(jwt(b -> b.issuedAt(AHORA.minusSeconds(7200)).expiresAt(AHORA.minusSeconds(3600))));
        assertThat(r.hasErrors()).isTrue();
    }

    @Test
    void claimsExtraNoMolestan() {
        var v = con(t -> jwt(b -> b.claims(c -> c.putAll(Map.of("hd", "primerplayer.cl")))));
        assertThat(v.verificar("t").email()).isEqualTo("ana@gmail.com");
    }
}
