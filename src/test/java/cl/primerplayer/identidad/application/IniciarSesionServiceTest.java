package cl.primerplayer.identidad.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cl.primerplayer.identidad.api.CredencialesInvalidasException;
import cl.primerplayer.identidad.api.CuentaBloqueadaException;
import cl.primerplayer.identidad.api.IniciarSesionUseCase.IniciarSesionCommand;
import cl.primerplayer.shared.api.Rol;
import cl.primerplayer.identidad.domain.TokenOpaco;
import cl.primerplayer.identidad.domain.Usuario;
import cl.primerplayer.shared.api.ExcepcionNegocio;

class IniciarSesionServiceTest {

    static final Instant AHORA = Instant.parse("2026-11-11T15:00:00Z");

    Fakes.UsuariosEnMemoria usuarios;
    Fakes.RefreshEnMemoria refresh;
    Fakes.HasherFalso hasher;
    IniciarSesionService servicio;

    @BeforeEach
    void setUp() {
        usuarios = new Fakes.UsuariosEnMemoria();
        refresh = new Fakes.RefreshEnMemoria();
        hasher = new Fakes.HasherFalso();
        var props = new IdentidadProperties(Duration.ofDays(30), Duration.ofHours(24),
                new IdentidadProperties.Bloqueo(3, Duration.ofMinutes(15)), Duration.ofSeconds(10));
        servicio = new IniciarSesionService(usuarios, hasher,
                new AperturaSesion(usuarios, refresh, new Fakes.EmisorFalso(), props), props,
                Clock.fixed(AHORA, ZoneOffset.UTC));
        usuarios.guardarNuevo(Usuario.registrarCliente("Misty", "misty@celeste.cl", "hash:secreta123", AHORA));
    }

    private IniciarSesionCommand cmd(String email, String password) {
        return new IniciarSesionCommand(email, password, "127.0.0.1", "JUnit");
    }

    @Test
    void credencialesCorrectas_emiteTokensYGuardaSoloElHashDelRefresh() {
        var sesion = servicio.iniciarSesion(cmd("  MISTY@celeste.cl ", "secreta123"));

        assertThat(sesion.accessToken()).isEqualTo("jwt-1");
        assertThat(sesion.expiresIn()).isEqualTo(900);
        assertThat(sesion.usuario().rol()).isEqualTo(Rol.CLIENTE);
        assertThat(refresh.guardados).singleElement().satisfies(rt -> {
            assertThat(rt.tokenHash()).isEqualTo(TokenOpaco.hashDe(sesion.refreshToken()));
            assertThat(rt.tokenHash()).isNotEqualTo(sesion.refreshToken());
            assertThat(rt.expiraEn()).isEqualTo(AHORA.plus(Duration.ofDays(30)));
        });
    }

    @Test
    void passwordIncorrecta_unauthorizedYSumaIntento() {
        assertThatThrownBy(() -> servicio.iniciarSesion(cmd("misty@celeste.cl", "otra")))
                .isInstanceOf(CredencialesInvalidasException.class)
                .extracting(e -> ((ExcepcionNegocio) e).code()).isEqualTo("unauthorized");
        assertThat(usuarios.porId.get(1L).intentosFallidos()).isEqualTo(1);
        assertThat(refresh.guardados).isEmpty();
    }

    @Test
    void correoInexistente_mismoErrorYComparaContraSenuelo() {
        assertThatThrownBy(() -> servicio.iniciarSesion(cmd("nadie@celeste.cl", "x")))
                .isInstanceOf(CredencialesInvalidasException.class);
        assertThat(hasher.comparaciones).isEqualTo(1);
    }

    @Test
    void alLlegarAlMaximoDeIntentos_accountLockedYNiLaCorrectaEntra() {
        servicio.iniciarSesion(cmd("misty@celeste.cl", "secreta123"));   // no afecta
        assertThatThrownBy(() -> servicio.iniciarSesion(cmd("misty@celeste.cl", "x1")))
                .isInstanceOf(CredencialesInvalidasException.class);
        assertThatThrownBy(() -> servicio.iniciarSesion(cmd("misty@celeste.cl", "x2")))
                .isInstanceOf(CredencialesInvalidasException.class);
        assertThatThrownBy(() -> servicio.iniciarSesion(cmd("misty@celeste.cl", "x3")))
                .isInstanceOf(CuentaBloqueadaException.class)
                .extracting(e -> ((ExcepcionNegocio) e).code()).isEqualTo("account-locked");

        assertThatThrownBy(() -> servicio.iniciarSesion(cmd("misty@celeste.cl", "secreta123")))
                .isInstanceOf(CuentaBloqueadaException.class);
    }

    @Test
    void exitoReiniciaElContador() {
        assertThatThrownBy(() -> servicio.iniciarSesion(cmd("misty@celeste.cl", "x1")))
                .isInstanceOf(CredencialesInvalidasException.class);
        servicio.iniciarSesion(cmd("misty@celeste.cl", "secreta123"));
        assertThat(usuarios.porId.get(1L).intentosFallidos()).isZero();
    }

    @Test
    void clienteAnonimoDeMostrador_noPuedeIniciarSesion() {
        usuarios.porId.put(99L, Usuario.reconstituir(99L, "mostrador@primerplayer.cl", "Mostrador", null,
                Rol.CLIENTE, true, true, AHORA, 0, null, AHORA, null));
        assertThatThrownBy(() -> servicio.iniciarSesion(cmd("mostrador@primerplayer.cl", "")))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void usuarioInactivo_noPuedeIniciarSesion() {
        usuarios.porId.put(50L, Usuario.reconstituir(50L, "brock@plateada.cl", "Brock", "hash:roca12345",
                Rol.VENDEDOR, false, false, AHORA, 0, null, AHORA, null));
        assertThatThrownBy(() -> servicio.iniciarSesion(cmd("brock@plateada.cl", "roca12345")))
                .isInstanceOf(CredencialesInvalidasException.class);
    }
}
