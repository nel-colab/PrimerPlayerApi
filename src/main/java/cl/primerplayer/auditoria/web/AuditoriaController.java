package cl.primerplayer.auditoria.web;

import java.time.OffsetDateTime;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonRawValue;

import cl.primerplayer.auditoria.api.ConsultarAuditoriaUseCase;
import cl.primerplayer.shared.api.FechasTienda;
import cl.primerplayer.shared.api.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin · Auditoría")
@SecurityRequirement(name = "bearer")
class AuditoriaController {

    private final ConsultarAuditoriaUseCase auditoria;

    @GetMapping("/api/v1/admin/auditoria")
    @Operation(summary = "Registro de auditoría (más reciente primero)",
            description = "Quién, cuándo, qué entidad y cómo estaba antes y después. Filtros opcionales.")
    Pagina<RegistroResponse> buscar(
            @Parameter(example = "usuario") @RequestParam(required = false) String entidad,
            @RequestParam(required = false) String entidadId,
            @Parameter(description = "fecha (día de Chile) o fecha-hora ISO") @RequestParam(required = false) String desde,
            @Parameter(description = "exclusivo") @RequestParam(required = false) String hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return auditoria.buscar(entidad, entidadId, FechasTienda.parse("desde", desde), FechasTienda.parse("hasta", hasta),
                page, size).map(r -> new RegistroResponse(r.id(), FechasTienda.enZona(r.ocurridoEn()), r.usuarioId(),
                r.usuarioNombre(), r.accion(), r.entidad(), r.entidadId(), r.antes(), r.despues(), r.ip()));
    }

    /** {@code before} y {@code after} van como JSON (no como texto escapado). */
    record RegistroResponse(long id, OffsetDateTime at, Long userId, String userName, String action, String entity,
                            String entityId, @JsonRawValue String before, @JsonRawValue String after, String ip) {}
}
