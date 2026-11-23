package cl.primerplayer.identidad.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cl.primerplayer.identidad.application.port.out.CuentaRepositorio;
import cl.primerplayer.identidad.application.port.out.CuentaRepositorio.FilaCuenta;
import cl.primerplayer.identidad.domain.TokenOpaco;
import cl.primerplayer.identidad.domain.TokenUnUso;
import cl.primerplayer.shared.api.Rol;

class EnlacesCuentaServiceTest {

    static final Instant AHORA = Instant.parse("2026-11-22T15:00:00Z");

    CuentaRepositorio cuentas;
    Fakes.TokensUnUsoEnMemoria tokens;
    EnlacesCuentaService servicio;

    @BeforeEach
    void setUp() {
        cuentas = mock(CuentaRepositorio.class);
        tokens = new Fakes.TokensUnUsoEnMemoria();
        var props = new IdentidadProperties(Duration.ofDays(30), Duration.ofHours(24),
                new IdentidadProperties.Bloqueo(5, Duration.ofMinutes(15)), Duration.ofSeconds(10));
        servicio = new EnlacesCuentaService(cuentas, tokens, Clock.fixed(AHORA, ZoneOffset.UTC), props,
                Duration.ofHours(1));
    }

    static FilaCuenta cuenta(boolean activo, Instant verificadoEn) {
        return new FilaCuenta(7, "ash@x.cl", "Ash", "hash", Rol.CLIENTE, activo, null, null, AHORA, verificadoEn, false);
    }

    @Test
    void verificacion_guardaSoloElHashYDevuelveElTokenConSuVigencia() {
        when(cuentas.porId(7)).thenReturn(Optional.of(cuenta(true, null)));

        var emitido = servicio.emitirVerificacion(7).orElseThrow();

        assertThat(emitido.vigencia()).isEqualTo(Duration.ofHours(24));
        assertThat(emitido.toString()).doesNotContain(emitido.valor());
        assertThat(tokens.guardados).singleElement().satisfies(t -> {
            assertThat(t.tipo()).isEqualTo(TokenUnUso.Tipo.VERIFICACION);
            assertThat(t.tokenHash()).isEqualTo(TokenOpaco.hashDe(emitido.valor())).isNotEqualTo(emitido.valor());
            assertThat(t.expiraEn()).isEqualTo(AHORA.plus(Duration.ofHours(24)));
        });
    }

    @Test
    void nuevaEmision_invalidaLosEnlacesAnterioresDelMismoTipo() {
        when(cuentas.porId(7)).thenReturn(Optional.of(cuenta(true, null)));

        servicio.emitirRecuperacion(7);
        servicio.emitirVerificacion(7);
        servicio.emitirRecuperacion(7);

        assertThat(tokens.guardados).extracting(TokenUnUso::tipo, TokenUnUso::expiraEn).containsExactly(
                org.assertj.core.groups.Tuple.tuple(TokenUnUso.Tipo.RECUPERACION, AHORA),   // vencido por el último
                org.assertj.core.groups.Tuple.tuple(TokenUnUso.Tipo.VERIFICACION, AHORA.plus(Duration.ofHours(24))),
                org.assertj.core.groups.Tuple.tuple(TokenUnUso.Tipo.RECUPERACION, AHORA.plus(Duration.ofHours(1))));
    }

    @Test
    void cuentaYaVerificada_noEmiteVerificacion() {
        when(cuentas.porId(7)).thenReturn(Optional.of(cuenta(true, AHORA)));

        assertThat(servicio.emitirVerificacion(7)).isEmpty();
        assertThat(tokens.guardados).isEmpty();
    }

    @Test
    void cuentaInactivaOInexistente_noEmiteNada() {
        when(cuentas.porId(7)).thenReturn(Optional.of(cuenta(false, null)));
        when(cuentas.porId(8)).thenReturn(Optional.empty());

        assertThat(servicio.emitirRecuperacion(7)).isEmpty();
        assertThat(servicio.emitirVerificacion(8)).isEmpty();
        assertThat(tokens.guardados).isEmpty();
    }
}
