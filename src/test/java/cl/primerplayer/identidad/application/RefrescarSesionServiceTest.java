package cl.primerplayer.identidad.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cl.primerplayer.identidad.api.IniciarSesionUseCase.SesionIniciada;
import cl.primerplayer.identidad.api.RefrescarSesionUseCase.RefrescarSesionCommand;
import cl.primerplayer.identidad.api.SesionInvalidaException;
import cl.primerplayer.identidad.domain.RefreshToken;
import cl.primerplayer.identidad.domain.TokenOpaco;
import cl.primerplayer.identidad.domain.Usuario;
import cl.primerplayer.shared.api.Rol;

class RefrescarSesionServiceTest {

    static final Instant T0 = Instant.parse("2026-11-11T15:00:00Z");

    /** Reloj que la prueba puede avanzar. */
    static final class RelojMovil extends Clock {
        Instant ahora = T0;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
        void avanzar(Duration d) { ahora = ahora.plus(d); }
    }

    Fakes.UsuariosEnMemoria usuarios;
    Fakes.RefreshEnMemoria refresh;
    RelojMovil reloj;
    RefrescarSesionService servicio;
    String tokenInicial;
    RefreshToken filaInicial;

    @BeforeEach
    void setUp() {
        usuarios = new Fakes.UsuariosEnMemoria();
        refresh = new Fakes.RefreshEnMemoria();
        reloj = new RelojMovil();
        var props = new IdentidadProperties(Duration.ofDays(30), Duration.ofHours(24),
                new IdentidadProperties.Bloqueo(5, Duration.ofMinutes(15)), Duration.ofSeconds(10));
        servicio = new RefrescarSesionService(refresh, usuarios, new Fakes.EmisorFalso(), props, reloj);

        Usuario u = usuarios.guardarNuevo(Usuario.crearVerificado("Ash", "ash@test.cl", "hash:x", Rol.CLIENTE, T0));
        TokenOpaco t = TokenOpaco.generar();
        tokenInicial = t.valor();
        filaInicial = RefreshToken.nuevaFamilia(u.id(), t, T0, Duration.ofDays(30), "127.0.0.1", "JUnit");
        refresh.guardar(filaInicial);
    }

    private SesionIniciada refrescar(String token) {
        return servicio.refrescar(new RefrescarSesionCommand(token, "127.0.0.1", "JUnit"));
    }

    @Test
    void vigente_rotaEnLaMismaFamiliaYEmiteAcceso() {
        reloj.avanzar(Duration.ofMinutes(20));
        SesionIniciada s = refrescar(tokenInicial);

        assertThat(s.accessToken()).isEqualTo("jwt-1");
        assertThat(s.refreshToken()).isNotEqualTo(tokenInicial);
        RefreshToken sucesor = refresh.buscarParaRotar(TokenOpaco.hashDe(s.refreshToken())).orElseThrow();
        assertThat(sucesor.familia()).isEqualTo(filaInicial.familia());
        assertThat(sucesor.expiraEn()).isEqualTo(reloj.ahora.plus(Duration.ofDays(30)));
        RefreshToken viejo = refresh.porId(filaInicial.id());
        assertThat(viejo.revocadoEn()).isEqualTo(reloj.ahora);
        assertThat(viejo.reemplazadoPor()).isEqualTo(sucesor.id());
    }

    @Test
    void reuso_despuesDeLaGracia_revocaTodaLaFamilia() {
        SesionIniciada s = refrescar(tokenInicial);
        reloj.avanzar(Duration.ofMinutes(5));

        assertThatThrownBy(() -> refrescar(tokenInicial)).isInstanceOf(SesionInvalidaException.class);
        // El token legítimo (sucesor) también quedó revocado: hay que volver a iniciar sesión
        assertThatThrownBy(() -> refrescar(s.refreshToken())).isInstanceOf(SesionInvalidaException.class);
        assertThat(refresh.guardados).allSatisfy(t -> assertThat(t.revocadoEn()).isNotNull());
    }

    @Test
    void carreraEntrePestanas_dentroDeLaGracia_401SinRevocarLaFamilia() {
        SesionIniciada ganadora = refrescar(tokenInicial);
        reloj.avanzar(Duration.ofSeconds(3));

        assertThatThrownBy(() -> refrescar(tokenInicial)).isInstanceOf(SesionInvalidaException.class);
        // La pestaña perdedora reintenta con la cookie nueva y funciona
        assertThat(refrescar(ganadora.refreshToken()).accessToken()).isEqualTo("jwt-1");
    }

    @Test
    void vencido_desconocido_vacioOrevocadoPorLogout_401() {
        assertThatThrownBy(() -> refrescar(null)).isInstanceOf(SesionInvalidaException.class);
        assertThatThrownBy(() -> refrescar("no-existe")).isInstanceOf(SesionInvalidaException.class);

        reloj.avanzar(Duration.ofDays(31));
        assertThatThrownBy(() -> refrescar(tokenInicial)).isInstanceOf(SesionInvalidaException.class);
    }

    @Test
    void usuarioDesactivado_401YRevocaLaFamilia() {
        Usuario u = usuarios.porId.get(1L);
        usuarios.porId.put(1L, Usuario.reconstituir(1L, u.email(), u.nombre(), u.passwordHash(), u.rol(), false,
                false, u.emailVerificadoEn(), 0, null, u.creadoEn(), null));
        assertThatThrownBy(() -> refrescar(tokenInicial)).isInstanceOf(SesionInvalidaException.class);
        assertThat(refresh.porId(filaInicial.id()).revocadoEn()).isNotNull();
    }
}
