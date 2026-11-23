package cl.primerplayer.eventos.web;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.primerplayer.eventos.api.SolicitudesCumpleanosUseCase;
import cl.primerplayer.eventos.api.SolicitudesCumpleanosUseCase.EstadoSolicitud;
import cl.primerplayer.eventos.web.dto.EventoDtos.AprobacionRequest;
import cl.primerplayer.eventos.web.dto.EventoDtos.RechazoRequest;
import cl.primerplayer.eventos.web.dto.EventoDtos.SolicitudRequest;
import cl.primerplayer.eventos.web.dto.EventoDtos.SolicitudResponse;
import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.UsuarioActual;
import cl.primerplayer.shared.api.ValidacionException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Solicitudes de cumpleaños: el cliente en {@code /me}, la tienda en {@code /admin}. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Cumpleaños")
@SecurityRequirement(name = "bearer")
class SolicitudesCumpleanosController {

    private final SolicitudesCumpleanosUseCase solicitudes;
    private final EventosWebMapper mapper;

    // ---------- cliente (el id sale siempre del token) ----------

    @PostMapping("/api/v1/me/solicitudes-cumpleanos")
    @PreAuthorize("hasAnyRole('CLIENTE','VENDEDOR','ADMIN')")
    @Operation(summary = "Solicitar un bloque para un cumpleaños",
            description = "Queda PENDIENTE hasta que la tienda la apruebe. Errores: validation (400), slot-taken (409).")
    ResponseEntity<SolicitudResponse> solicitar(@Valid @RequestBody SolicitudRequest req,
                                                @AuthenticationPrincipal UsuarioActual usuario) {
        var s = solicitudes.solicitar(usuario.id(), mapper.toNueva(req));
        return ResponseEntity.created(URI.create("/api/v1/me/solicitudes-cumpleanos")).body(mapper.toCliente(s));
    }

    @GetMapping("/api/v1/me/solicitudes-cumpleanos")
    @PreAuthorize("hasAnyRole('CLIENTE','VENDEDOR','ADMIN')")
    @Operation(summary = "Mis solicitudes de cumpleaños")
    List<SolicitudResponse> mias(@AuthenticationPrincipal UsuarioActual usuario) {
        return solicitudes.deCliente(usuario.id()).stream().map(mapper::toCliente).toList();
    }

    @PostMapping("/api/v1/me/solicitudes-cumpleanos/{id}/cancelacion")
    @PreAuthorize("hasAnyRole('CLIENTE','VENDEDOR','ADMIN')")
    @Operation(summary = "Cancelar una solicitud pendiente", description = "Ajena: 404. No pendiente: 409 request-not-pending.")
    SolicitudResponse cancelar(@PathVariable long id, @AuthenticationPrincipal UsuarioActual usuario) {
        return mapper.toCliente(solicitudes.cancelar(usuario.id(), id));
    }

    // ---------- tienda ----------

    @GetMapping("/api/v1/admin/solicitudes-cumpleanos")
    @PreAuthorize("hasAnyRole('VENDEDOR','ADMIN')")
    @Operation(summary = "Bandeja de solicitudes", description = "estado = PENDIENTE (por defecto) | APROBADA | RECHAZADA | CANCELADA | todas")
    Pagina<SolicitudResponse> bandeja(@RequestParam(defaultValue = "PENDIENTE") String estado,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "24") int size) {
        if (page < 0 || size < 1 || size > Filtros.MAX_PAGE_SIZE) {
            throw new ValidacionException("size", "page >= 0 y size entre 1 y " + Filtros.MAX_PAGE_SIZE + ".");
        }
        return solicitudes.buscar(estado(estado), page, size).map(mapper::toTienda);
    }

    @PostMapping("/api/v1/admin/solicitudes-cumpleanos/{id}/aprobacion")
    @PreAuthorize("hasAnyRole('VENDEDOR','ADMIN')")
    @Operation(summary = "Aprobar: crea y publica el evento que reserva el bloque",
            description = "Errores: slot-taken (409) si el bloque ya no está libre; request-not-pending (409).")
    SolicitudResponse aprobar(@PathVariable long id, @Valid @RequestBody(required = false) AprobacionRequest req,
                              @AuthenticationPrincipal UsuarioActual usuario) {
        var aprobacion = mapper.toAprobacion(req == null ? new AprobacionRequest(null, null, null, null) : req);
        return mapper.toTienda(solicitudes.aprobar(id, aprobacion, usuario.id()));
    }

    @PostMapping("/api/v1/admin/solicitudes-cumpleanos/{id}/rechazo")
    @PreAuthorize("hasAnyRole('VENDEDOR','ADMIN')")
    SolicitudResponse rechazar(@PathVariable long id, @Valid @RequestBody RechazoRequest req,
                               @AuthenticationPrincipal UsuarioActual usuario) {
        return mapper.toTienda(solicitudes.rechazar(id, req.motivo(), usuario.id()));
    }

    private static EstadoSolicitud estado(String valor) {
        if (valor.equalsIgnoreCase("todas")) {
            return null;
        }
        try {
            return EstadoSolicitud.valueOf(valor.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidacionException("estado", "Valor no permitido: " + valor);
        }
    }
}
