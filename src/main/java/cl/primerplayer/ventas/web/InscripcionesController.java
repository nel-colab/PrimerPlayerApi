package cl.primerplayer.ventas.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.primerplayer.shared.api.UsuarioActual;
import cl.primerplayer.ventas.api.InscripcionesVentaUseCase;
import cl.primerplayer.ventas.web.dto.InscripcionResponse;
import cl.primerplayer.ventas.web.dto.InscritosResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Inscripciones a eventos: las del cliente (el id sale del token) y los inscritos de un evento para la tienda. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Inscripciones a eventos")
@SecurityRequirement(name = "bearer")
class InscripcionesController {

    private final InscripcionesVentaUseCase inscripciones;
    private final VentasWebMapper mapper;

    // ---------- cliente ----------

    @GetMapping("/api/v1/me/inscripciones")
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(summary = "Mis inscripciones a eventos", description = "Próximas primero (la más cercana arriba), luego las pasadas.")
    List<InscripcionResponse> mias(@AuthenticationPrincipal UsuarioActual usuario) {
        return inscripciones.delCliente(usuario.id()).stream().map(mapper::toInscripcion).toList();
    }

    @PostMapping("/api/v1/me/inscripciones/{id}/cancelacion")
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(summary = "Cancelar una inscripción pagada",
            description = "Libera los cupos. No reembolsa el pago automáticamente (lo gestiona la tienda). Errores: "
                    + "404 item-unavailable (no existe o es ajena), 409 registration-not-cancellable (pendiente de pago, "
                    + "ya cancelada o expirada), 409 event-past (el evento ya comenzó).")
    InscripcionResponse cancelar(@PathVariable long id, @AuthenticationPrincipal UsuarioActual usuario) {
        return mapper.toInscripcion(inscripciones.cancelar(usuario.id(), id));
    }

    // ---------- tienda ----------

    @GetMapping("/api/v1/admin/eventos/{id}/inscritos")
    @PreAuthorize("hasAnyRole('VENDEDOR','ADMIN')")
    @Operation(summary = "Inscritos de un evento", description = "Vigentes primero; cancelados y expirados al final. "
            + "404 item-unavailable si el evento no existe o fue eliminado.")
    InscritosResponse inscritos(@PathVariable long id) {
        return mapper.toInscritos(inscripciones.inscritos(id));
    }
}
