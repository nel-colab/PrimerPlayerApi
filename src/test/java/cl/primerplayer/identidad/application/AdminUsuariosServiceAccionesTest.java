package cl.primerplayer.identidad.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import cl.primerplayer.identidad.api.AccionSobreSiMismoException;
import cl.primerplayer.identidad.api.AdminUsuariosUseCase.Baneo;
import cl.primerplayer.identidad.api.AdminUsuariosUseCase.Filtro;
import cl.primerplayer.identidad.api.AdminUsuariosUseCase.Indicadores;
import cl.primerplayer.identidad.api.AdminUsuariosUseCase.ListaNegra;
import cl.primerplayer.identidad.api.AdminUsuariosUseCase.UsuarioAdmin;
import cl.primerplayer.identidad.api.YaVerificadoException;
import cl.primerplayer.identidad.application.port.out.AdminUsuariosRepositorio;
import cl.primerplayer.identidad.application.port.out.CuentaRepositorio;
import cl.primerplayer.identidad.events.RecuperacionSolicitada;
import cl.primerplayer.identidad.events.UsuarioAdministrado;
import cl.primerplayer.identidad.events.VerificacionSolicitada;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import cl.primerplayer.shared.api.Rol;
import cl.primerplayer.shared.api.ValidacionException;

/** Acciones del panel de usuarios: validaciones, auditoría (JSON) y eventos. */
class AdminUsuariosServiceAccionesTest {

    static final Instant AHORA = Instant.parse("2026-11-26T15:00:00Z");
    static final long ACTOR = 1;
    static final long ID = 5;

    final AdminUsuariosRepositorio repo = mock(AdminUsuariosRepositorio.class);
    final CuentaRepositorio cuentas = mock(CuentaRepositorio.class);
    final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    final AdminUsuariosService servicio = new AdminUsuariosService(repo, cuentas, eventos, Clock.fixed(AHORA, ZoneOffset.UTC));

    static UsuarioAdmin usuario(Rol rol, boolean verificado, Baneo baneo, ListaNegra lista) {
        return new UsuarioAdmin(ID, "ana@demo.cl", "Ana", rol, null, verificado, baneo, lista, false, AHORA, null);
    }

    void existe(UsuarioAdmin u) {
        when(repo.bloquear(anyLong(), any())).thenReturn(Optional.of(u));
    }

    UsuarioAdministrado auditoria() {
        ArgumentCaptor<Object> ev = ArgumentCaptor.forClass(Object.class);
        verify(eventos, org.mockito.Mockito.atLeastOnce()).publishEvent(ev.capture());
        return ev.getAllValues().stream().filter(UsuarioAdministrado.class::isInstance)
                .map(UsuarioAdministrado.class::cast).findFirst().orElseThrow();
    }

    @BeforeEach
    void clienteVerificado() {
        existe(usuario(Rol.CLIENTE, true, null, null));
        when(repo.adminsActivos(any())).thenReturn(2L);
    }

    // ---------- lecturas ----------

    @Test
    void buscarPaginaYValidaRango() {
        Filtro f = new Filtro("ana", Set.of(Rol.CLIENTE), null, 0, 20);
        when(repo.buscar(f, AHORA)).thenReturn(List.of(usuario(Rol.CLIENTE, true, null, null)));
        when(repo.contar(f, AHORA)).thenReturn(41L);

        var pagina = servicio.buscar(f);

        assertThat(pagina.content()).hasSize(1);
        assertThat(pagina.totalElements()).isEqualTo(41);
        assertThatThrownBy(() -> servicio.buscar(new Filtro(null, null, null, -1, 20))).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> servicio.buscar(new Filtro(null, null, null, 0, 0))).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> servicio.buscar(new Filtro(null, null, null, 0, 101))).isInstanceOf(ValidacionException.class);
    }

    @Test
    void indicadoresDelRepositorio() {
        Indicadores i = new Indicadores(10, 7, 3, 1, 1, 2);
        when(repo.indicadores(AHORA)).thenReturn(i);
        assertThat(servicio.indicadores()).isEqualTo(i);
    }

    // ---------- rol ----------

    @Test
    void cambiarRolAuditaAntesYDespues() {
        servicio.cambiarRol(ACTOR, ID, Rol.VENDEDOR);

        verify(repo).cambiarRol(ID, Rol.VENDEDOR, AHORA);
        UsuarioAdministrado a = auditoria();
        assertThat(a.accion()).isEqualTo("ROL_CAMBIADO");
        assertThat(a.antes()).isEqualTo("{\"rol\": \"CLIENTE\"}");
        assertThat(a.despues()).isEqualTo("{\"rol\": \"VENDEDOR\"}");
    }

    @Test
    void cambiarAlMismoRolNoHaceNada() {
        UsuarioAdmin r = servicio.cambiarRol(ACTOR, ID, Rol.CLIENTE);
        assertThat(r.rol()).isEqualTo(Rol.CLIENTE);
        verify(repo, never()).cambiarRol(anyLong(), any(), any());
    }

    @Test
    void degradarAdminCuandoQuedanOtros() {
        existe(usuario(Rol.ADMIN, true, null, null));
        servicio.cambiarRol(ACTOR, ID, Rol.VENDEDOR);
        verify(repo).cambiarRol(ID, Rol.VENDEDOR, AHORA);
    }

    @Test
    void degradarAdminBaneadoNoCuentaComoActivo() {
        existe(usuario(Rol.ADMIN, true, new Baneo("x", null, AHORA), null));
        when(repo.adminsActivos(any())).thenReturn(1L);

        servicio.cambiarRol(ACTOR, ID, Rol.CLIENTE);

        verify(repo).cambiarRol(ID, Rol.CLIENTE, AHORA);
    }

    @Test
    void accionesSobreSiMismoSeRechazan() {
        assertThatThrownBy(() -> servicio.cambiarRol(ID, ID, Rol.ADMIN)).isInstanceOf(AccionSobreSiMismoException.class);
        assertThatThrownBy(() -> servicio.banear(ID, ID, "m", null)).isInstanceOf(AccionSobreSiMismoException.class);
        assertThatThrownBy(() -> servicio.ponerEnListaNegra(ID, ID, "m")).isInstanceOf(AccionSobreSiMismoException.class);
        assertThatThrownBy(() -> servicio.cerrarSesiones(ID, ID)).isInstanceOf(AccionSobreSiMismoException.class);
    }

    @Test
    void usuarioInexistente() {
        when(repo.bloquear(anyLong(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.quitarBaneo(ACTOR, 99)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ---------- baneo ----------

    @Test
    void banearTemporalRevocaSesionesYEscapaElMotivo() {
        Instant hasta = AHORA.plus(Duration.ofDays(7));

        servicio.banear(ACTOR, ID, "  Insultó \"al\" staff\n\ty \\ más \u0001 ", hasta);

        verify(repo).banear(ID, "Insultó \"al\" staff\n\ty \\ más \u0001", hasta, AHORA);
        verify(cuentas).revocarSesiones(ID, AHORA);
        UsuarioAdministrado a = auditoria();
        assertThat(a.antes()).isNull();
        assertThat(a.despues()).isEqualTo(
                "{\"motivo\": \"Insultó \\\"al\\\" staff\\n\\ty \\\\ más \\u0001\", \"hasta\": \"" + hasta + "\"}");
    }

    @Test
    void banearPermanenteGuardaHastaNulo() {
        servicio.banear(ACTOR, ID, "spam", null);

        assertThat(auditoria().despues()).isEqualTo("{\"motivo\": \"spam\", \"hasta\": null}");
    }

    @Test
    void banearConMotivoOFechaInvalidos() {
        assertThatThrownBy(() -> servicio.banear(ACTOR, ID, null, null)).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> servicio.banear(ACTOR, ID, " ", null)).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> servicio.banear(ACTOR, ID, "x".repeat(301), null)).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> servicio.banear(ACTOR, ID, "m", AHORA)).isInstanceOf(ValidacionException.class);
        verify(repo, never()).banear(anyLong(), any(), any(), any());
    }

    @Test
    void banearAdminCuandoQuedanOtros() {
        existe(usuario(Rol.ADMIN, true, null, null));
        servicio.banear(ACTOR, ID, "m", null);
        verify(repo).banear(ID, "m", null, AHORA);
    }

    @Test
    void quitarBaneoAuditaElMotivoAnterior() {
        existe(usuario(Rol.CLIENTE, true, new Baneo("spam", null, AHORA), null));
        servicio.quitarBaneo(ACTOR, ID);
        verify(repo).quitarBaneo(ID, AHORA);
        assertThat(auditoria().antes()).isEqualTo("{\"motivo\": \"spam\"}");
    }

    @Test
    void quitarBaneoSinBaneoVigente() {
        servicio.quitarBaneo(ACTOR, ID);
        assertThat(auditoria().antes()).isNull();
    }

    // ---------- lista negra ----------

    @Test
    void listaNegraAgregarYQuitar() {
        servicio.ponerEnListaNegra(ACTOR, ID, "contracargo");
        verify(repo).ponerEnListaNegra(ID, "contracargo", AHORA);

        existe(usuario(Rol.CLIENTE, true, null, new ListaNegra("contracargo", AHORA)));
        servicio.quitarDeListaNegra(ACTOR, ID);
        verify(repo).quitarDeListaNegra(ID, AHORA);
    }

    @Test
    void quitarDeListaNegraSinEstarEnElla() {
        servicio.quitarDeListaNegra(ACTOR, ID);
        assertThat(auditoria().accion()).isEqualTo("LISTA_NEGRA_QUITADO");
        assertThat(auditoria().antes()).isNull();
    }

    // ---------- contraseña, verificación y sesiones ----------

    @Test
    void solicitarCambioPasswordMarcaYEnviaCorreo() {
        servicio.solicitarCambioPassword(ACTOR, ID);

        verify(repo).marcarCambioPassword(ID, AHORA);
        verify(eventos).publishEvent(any(RecuperacionSolicitada.class));
    }

    @Test
    void reenviarVerificacionSoloSiNoEstaVerificado() {
        assertThatThrownBy(() -> servicio.reenviarVerificacion(ACTOR, ID)).isInstanceOf(YaVerificadoException.class);

        existe(usuario(Rol.CLIENTE, false, null, null));
        servicio.reenviarVerificacion(ACTOR, ID);
        verify(eventos).publishEvent(any(VerificacionSolicitada.class));
    }

    @Test
    void cerrarSesionesAuditaCuantas() {
        when(cuentas.revocarSesiones(ID, AHORA)).thenReturn(3);

        servicio.cerrarSesiones(ACTOR, ID);

        assertThat(auditoria().despues()).isEqualTo("{\"sesionesRevocadas\": \"3\"}");
    }
}
