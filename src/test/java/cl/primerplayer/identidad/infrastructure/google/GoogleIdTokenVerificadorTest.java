package cl.primerplayer.identidad.infrastructure.google;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;

import org.junit.jupiter.api.Test;

import cl.primerplayer.identidad.api.GoogleNoDisponibleException;
import cl.primerplayer.identidad.api.GoogleTokenInvalidoException;

class GoogleIdTokenVerificadorTest {

    @Test
    void sinClientId_googleUnavailable() {
        var v = new GoogleIdTokenVerificador(new GoogleProperties(" "), Clock.systemUTC());
        assertThatThrownBy(() -> v.verificar("x.y.z")).isInstanceOf(GoogleNoDisponibleException.class);
    }

    @Test
    void tokenVacioOMalformado_invalidGoogleToken_sinLlamarAGoogle() {
        var v = new GoogleIdTokenVerificador(new GoogleProperties("123.apps.googleusercontent.com"), Clock.systemUTC());
        assertThatThrownBy(() -> v.verificar(" ")).isInstanceOf(GoogleTokenInvalidoException.class);
        assertThatThrownBy(() -> v.verificar("no-es-un-jwt")).isInstanceOf(GoogleTokenInvalidoException.class);
    }
}
