package cl.primerplayer.identidad.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cl.primerplayer.identidad.domain.TokenOpaco;

class CerrarSesionServiceTest {

    Fakes.RefreshEnMemoria refresh;
    CerrarSesionService servicio;

    @BeforeEach
    void setUp() {
        refresh = new Fakes.RefreshEnMemoria();
        servicio = new CerrarSesionService(refresh, Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void conToken_revocaPorHashNuncaPorValorEnClaro() {
        servicio.cerrarSesion("valor-opaco");
        assertThat(refresh.hashesRevocados).containsExactly(TokenOpaco.hashDe("valor-opaco"));
    }

    @Test
    void sinToken_noHaceNada() {
        servicio.cerrarSesion(null);
        servicio.cerrarSesion(" ");
        assertThat(refresh.hashesRevocados).isEmpty();
    }
}
