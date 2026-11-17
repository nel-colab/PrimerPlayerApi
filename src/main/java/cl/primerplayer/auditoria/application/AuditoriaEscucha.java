package cl.primerplayer.auditoria.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import cl.primerplayer.auditoria.infrastructure.AuditoriaRepositorio;
import cl.primerplayer.identidad.events.UsuarioAdministrado;
import lombok.RequiredArgsConstructor;

/** Escribe la auditoría a partir de eventos de dominio (después del commit de quien actuó; outbox de Modulith). */
@Component
@RequiredArgsConstructor
class AuditoriaEscucha {

    private final AuditoriaRepositorio auditoria;

    @ApplicationModuleListener
    void usuarioAdministrado(UsuarioAdministrado e) {
        auditoria.registrar(e.ocurridoEn(), e.actorId(), e.accion(), "usuario", String.valueOf(e.usuarioId()),
                e.antes(), e.despues());
    }
}
