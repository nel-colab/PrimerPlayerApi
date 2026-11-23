package cl.primerplayer.identidad.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import cl.primerplayer.identidad.api.AdminUsuariosUseCase.UsuarioAdmin;
import cl.primerplayer.identidad.api.UltimoAdminException;
import cl.primerplayer.identidad.application.port.out.AdminUsuariosRepositorio;
import cl.primerplayer.identidad.application.port.out.CuentaRepositorio;
import cl.primerplayer.shared.api.Rol;

/** El último administrador activo no se puede degradar ni banear (la carrera entre dos admins la cierra el advisory lock). */
class AdminUsuariosServiceTest {

    static final Instant AHORA = Instant.parse("2026-11-13T15:00:00Z");

    AdminUsuariosRepositorio repo;
    AdminUsuariosService servicio;

    @BeforeEach
    void setUp() {
        repo = mock(AdminUsuariosRepositorio.class);
        servicio = new AdminUsuariosService(repo, mock(CuentaRepositorio.class), mock(ApplicationEventPublisher.class),
                Clock.fixed(AHORA, ZoneOffset.UTC));
        when(repo.bloquear(anyLong(), any())).thenReturn(Optional.of(new UsuarioAdmin(2, "b@x.cl", "B", Rol.ADMIN, null,
                true, null, null, false, AHORA, null)));
        when(repo.adminsActivos(any())).thenReturn(1L);
    }

    @Test
    void degradarAlUltimoAdmin_lastAdmin() {
        assertThatThrownBy(() -> servicio.cambiarRol(1, 2, Rol.VENDEDOR)).isInstanceOf(UltimoAdminException.class);
        verify(repo).bloquearAdmins();
        verify(repo, never()).cambiarRol(anyLong(), any(), any());
    }

    @Test
    void banearAlUltimoAdmin_lastAdmin() {
        assertThatThrownBy(() -> servicio.banear(1, 2, "motivo", null)).isInstanceOf(UltimoAdminException.class);
        verify(repo, never()).banear(anyLong(), any(), any(), any());
    }
}
