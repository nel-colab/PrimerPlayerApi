package cl.primerplayer.identidad.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import cl.primerplayer.identidad.api.CodigoInvalidoException;
import cl.primerplayer.identidad.api.CuentaUseCase.Perfil;
import cl.primerplayer.identidad.api.DemasiadosIntentosException;
import cl.primerplayer.identidad.api.YaVerificadoException;
import cl.primerplayer.identidad.application.port.out.CuentaRepositorio;
import cl.primerplayer.identidad.application.port.out.CuentaRepositorio.CodigoPendiente;
import cl.primerplayer.identidad.application.port.out.CuentaRepositorio.FilaCuenta;
import cl.primerplayer.identidad.application.port.out.HasherPassword;
import cl.primerplayer.identidad.application.port.out.UsuarioRepositorio;
import cl.primerplayer.identidad.domain.CodigoVerificacion;
import cl.primerplayer.identidad.domain.TokenUnUso;
import cl.primerplayer.identidad.domain.Usuario;
import cl.primerplayer.identidad.events.RecuperacionSolicitada;
import cl.primerplayer.identidad.events.VerificacionSolicitada;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import cl.primerplayer.shared.api.Rol;
import cl.primerplayer.shared.api.ValidacionException;

class CuentaServiceTest {

    static final Instant AHORA = Instant.parse("2026-11-26T15:00:00Z");
    static final long ID = 42;
    static final String EMAIL = "ana@demo.cl";

    final CuentaRepositorio cuentas = mock(CuentaRepositorio.class);
    final UsuarioRepositorio usuarios = mock(UsuarioRepositorio.class);
    final HasherPassword hasher = mock(HasherPassword.class);
    final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    final CuentaService service = new CuentaService(cuentas, usuarios, hasher, eventos, Clock.fixed(AHORA, ZoneOffset.UTC));

    static FilaCuenta fila(boolean activo, String hash, Instant verificado) {
        return new FilaCuenta(ID, EMAIL, "Ana", hash, Rol.CLIENTE, activo, "+56 9 1234 5678", "12345678-5",
                AHORA.minus(Duration.ofDays(30)), verificado, false);
    }

    @BeforeEach
    void cuentaActiva() {
        when(cuentas.porId(ID)).thenReturn(Optional.of(fila(true, "hash", AHORA)));
        when(hasher.hashear(anyString())).thenAnswer(i -> "h(" + i.getArgument(0) + ")");
    }

    // ---------- perfil ----------

    @Test
    void perfilDeCuentaActiva() {
        Perfil p = service.perfil(ID);
        assertThat(p.email()).isEqualTo(EMAIL);
        assertThat(p.emailVerificado()).isTrue();
        assertThat(p.miembroDesde()).isEqualTo(AHORA.minus(Duration.ofDays(30)));
    }

    @Test
    void perfilDeCuentaInexistenteOInactiva() {
        when(cuentas.porId(1)).thenReturn(Optional.empty());
        when(cuentas.porId(2)).thenReturn(Optional.of(fila(false, "hash", null)));

        assertThatThrownBy(() -> service.perfil(1)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> service.perfil(2)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void actualizarPerfilNormalizaYGuarda() {
        service.actualizarPerfil(ID, "  Ana Pérez ", " +56 9 8765 4321 ", "12.345.678-5");
        verify(cuentas).actualizarPerfil(ID, "Ana Pérez", "+56 9 8765 4321", "12345678-5", AHORA);
    }

    @Test
    void actualizarPerfilSinTelefonoNiRut() {
        service.actualizarPerfil(ID, "Ana", " ", null);
        verify(cuentas).actualizarPerfil(ID, "Ana", null, null, AHORA);
    }

    @Test
    void actualizarPerfilConNombreInvalido() {
        assertThatThrownBy(() -> service.actualizarPerfil(ID, null, null, null)).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> service.actualizarPerfil(ID, "   ", null, null)).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> service.actualizarPerfil(ID, "x".repeat(121), null, null))
                .isInstanceOf(ValidacionException.class);
        verify(cuentas, never()).actualizarPerfil(anyLong(), any(), any(), any(), any());
    }

    // ---------- teléfono y RUT ----------

    @ParameterizedTest
    @ValueSource(strings = {"1234567", "+56 9 abc 5678", "1234567890123456", "+56 9 1234 5678 9 0 1 2"})
    void telefonoInvalido(String t) {
        assertThatThrownBy(() -> CuentaService.telefono(t)).isInstanceOf(ValidacionException.class);
    }

    @Test
    void telefonoValido() {
        assertThat(CuentaService.telefono("912345678")).isEqualTo("912345678");
        assertThat(CuentaService.telefono("+56-9-1234-5678")).isEqualTo("+56-9-1234-5678");
    }

    @Test
    void rutValidoConDigitoNumericoKyCero() {
        assertThat(CuentaService.rut("12.345.678-5")).isEqualTo("12345678-5");
        assertThat(CuentaService.rut("10000013k")).isEqualTo("10000013-K");
        assertThat(CuentaService.rut("1000013-0")).isEqualTo("1000013-0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "123-4", "12.345.678-9"})
    void rutInvalido(String r) {
        assertThatThrownBy(() -> CuentaService.rut(r)).isInstanceOf(ValidacionException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rutYTelefonoVaciosSonNulos(String v) {
        assertThat(CuentaService.rut(v)).isNull();
        assertThat(CuentaService.telefono(v)).isNull();
    }

    // ---------- contraseña ----------

    @Test
    void cambiarPasswordConLaActualCorrecta() {
        when(hasher.coincide("vieja-clave", "hash")).thenReturn(true);

        service.cambiarPassword(ID, "vieja-clave", "nueva-clave");

        verify(cuentas).cambiarPassword(ID, "h(nueva-clave)", AHORA);
        verify(cuentas).revocarSesiones(ID, AHORA);
    }

    @Test
    void cambiarPasswordConActualIncorrectaNulaOSinPassword() {
        when(hasher.coincide(any(), any())).thenReturn(false);
        assertThatThrownBy(() -> service.cambiarPassword(ID, "otra", "nueva-clave")).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> service.cambiarPassword(ID, null, "nueva-clave")).isInstanceOf(ValidacionException.class);

        when(cuentas.porId(ID)).thenReturn(Optional.of(fila(true, null, AHORA)));   // cuenta de Google
        assertThatThrownBy(() -> service.cambiarPassword(ID, "x", "nueva-clave")).isInstanceOf(ValidacionException.class);
        verify(cuentas, never()).cambiarPassword(anyLong(), any(), any());
    }

    @Test
    void passwordNuevaFueraDeRango() {
        when(hasher.coincide("vieja-clave", "hash")).thenReturn(true);
        assertThatThrownBy(() -> service.cambiarPassword(ID, "vieja-clave", "corta")).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> service.cambiarPassword(ID, "vieja-clave", null)).isInstanceOf(ValidacionException.class);
        // 73 bytes: BCrypt ignoraría el resto
        assertThatThrownBy(() -> service.cambiarPassword(ID, "vieja-clave", "ñ".repeat(37))).isInstanceOf(ValidacionException.class);
    }

    // ---------- recuperación ----------

    static Usuario usuario(boolean activo) {
        return Usuario.reconstituir(ID, EMAIL, "Ana", "hash", Rol.CLIENTE, activo, false, AHORA, 0, null, AHORA, null);
    }

    @Test
    void solicitarRecuperacionPublicaEventoSoloSiPuedeIniciarSesion() {
        when(usuarios.buscarPorEmail(EMAIL)).thenReturn(Optional.of(usuario(true)));

        service.solicitarRecuperacion("  ANA@demo.cl ");

        ArgumentCaptor<RecuperacionSolicitada> ev = ArgumentCaptor.forClass(RecuperacionSolicitada.class);
        verify(eventos).publishEvent(ev.capture());
        assertThat(ev.getValue().email()).isEqualTo(EMAIL);
    }

    @Test
    void solicitarRecuperacionDeCuentaInactivaOInexistenteNoRevelaNada() {
        when(usuarios.buscarPorEmail(EMAIL)).thenReturn(Optional.of(usuario(false)));
        service.solicitarRecuperacion(EMAIL);
        service.solicitarRecuperacion("nadie@demo.cl");
        service.solicitarRecuperacion(null);
        service.solicitarRecuperacion(" ");

        verifyNoInteractions(eventos);
    }

    // ---------- códigos ----------

    final UUID tokenId = UUID.randomUUID();

    void codigoPendiente(TokenUnUso.Tipo tipo, String codigo) {
        when(cuentas.idPorEmail(EMAIL)).thenReturn(Optional.of(ID));
        when(cuentas.intentosFallidosDesde(eq(ID), eq(tipo), any())).thenReturn(0);
        when(cuentas.bloquearCodigoPendiente(ID, tipo, AHORA))
                .thenReturn(Optional.of(new CodigoPendiente(tokenId, CodigoVerificacion.hash(tokenId, codigo))));
    }

    @Test
    void restablecerConCodigoCorrecto() {
        codigoPendiente(TokenUnUso.Tipo.RECUPERACION, "482913");

        service.restablecerPassword(EMAIL, "482 913", "nueva-clave");

        verify(cuentas).marcarUsado(tokenId, AHORA);
        verify(cuentas).cambiarPassword(ID, "h(nueva-clave)", AHORA);
        verify(cuentas).revocarSesiones(ID, AHORA);
        verify(cuentas).marcarVerificado(ID, AHORA);
    }

    @Test
    void verificarConCodigoCorrecto() {
        codigoPendiente(TokenUnUso.Tipo.VERIFICACION, "000123");

        service.verificarEmail(EMAIL, "000-123");

        verify(cuentas).marcarVerificado(ID, AHORA);
    }

    @Test
    void codigoErradoSumaIntento() {
        codigoPendiente(TokenUnUso.Tipo.VERIFICACION, "111111");

        assertThatThrownBy(() -> service.verificarEmail(EMAIL, "222222")).isInstanceOf(CodigoInvalidoException.class);

        verify(cuentas).registrarIntentoFallido(tokenId, CodigoVerificacion.MAX_INTENTOS, AHORA);
        verify(cuentas, never()).marcarUsado(any(), any());
    }

    @Test
    void codigoMalFormadoOEmailVacioNoConsultaLaBase() {
        assertThatThrownBy(() -> service.verificarEmail(EMAIL, "12ab56")).isInstanceOf(CodigoInvalidoException.class);
        assertThatThrownBy(() -> service.verificarEmail(null, "123456")).isInstanceOf(CodigoInvalidoException.class);
        assertThatThrownBy(() -> service.verificarEmail(" ", "123456")).isInstanceOf(CodigoInvalidoException.class);
        verify(cuentas, never()).idPorEmail(any());
    }

    @Test
    void correoDesconocidoOSinCodigoPendiente() {
        when(cuentas.idPorEmail(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verificarEmail("nadie@demo.cl", "123456")).isInstanceOf(CodigoInvalidoException.class);

        when(cuentas.idPorEmail(EMAIL)).thenReturn(Optional.of(ID));
        when(cuentas.bloquearCodigoPendiente(eq(ID), any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verificarEmail(EMAIL, "123456")).isInstanceOf(CodigoInvalidoException.class);
    }

    @Test
    void demasiadosIntentosEnLaHoraBloquea() {
        when(cuentas.idPorEmail(EMAIL)).thenReturn(Optional.of(ID));
        when(cuentas.intentosFallidosDesde(ID, TokenUnUso.Tipo.RECUPERACION, AHORA.minus(Duration.ofHours(1))))
                .thenReturn(CuentaService.MAX_INTENTOS_POR_HORA);

        assertThatThrownBy(() -> service.restablecerPassword(EMAIL, "123456", "nueva-clave"))
                .isInstanceOf(DemasiadosIntentosException.class);
        verify(cuentas, never()).bloquearCodigoPendiente(anyLong(), any(), any());
    }

    // ---------- reenviar verificación ----------

    @Test
    void reenviarVerificacionPublicaEvento() {
        when(cuentas.porId(ID)).thenReturn(Optional.of(fila(true, "hash", null)));

        service.reenviarVerificacion(ID);

        verify(eventos).publishEvent(any(VerificacionSolicitada.class));
    }

    @Test
    void reenviarVerificacionYaVerificada() {
        assertThatThrownBy(() -> service.reenviarVerificacion(ID)).isInstanceOf(YaVerificadoException.class);
        verifyNoInteractions(eventos);
    }
}
