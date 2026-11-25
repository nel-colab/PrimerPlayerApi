package cl.primerplayer.catalogo.web;

import java.util.List;
import java.util.Locale;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.primerplayer.catalogo.api.RevisionesProductoUseCase;
import cl.primerplayer.catalogo.api.RevisionesProductoUseCase.EstadoRevision;
import cl.primerplayer.catalogo.api.RevisionesProductoUseCase.CambiosAprobacion;
import cl.primerplayer.catalogo.web.dto.RevisionDtos.AprobacionRequest;
import cl.primerplayer.catalogo.web.dto.RevisionDtos.RechazoRequest;
import cl.primerplayer.catalogo.web.dto.RevisionDtos.RevisionResponse;
import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.UsuarioActual;
import cl.primerplayer.shared.api.ValidacionException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Autorización de productos. VENDEDOR y ADMIN ven la bandeja y el historial; solo ADMIN aprueba o rechaza.
 */
@RestController
@RequestMapping("/api/v1/admin/productos")
@PreAuthorize("hasAnyRole('VENDEDOR','ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin · Aprobación de productos")
@SecurityRequirement(name = "bearer")
class AdminRevisionesProductoController {

    private static final int MAX_PAGE_SIZE = 100;

    private final RevisionesProductoUseCase revisiones;
    private final CatalogoWebMapper mapper;

    @GetMapping("/revisiones")
    @Operation(summary = "Bandeja de revisiones",
            description = "estado = PENDIENTE (por defecto) | APROBADA | RECHAZADA | DESCARTADA | todas. "
                    + "Las más antiguas primero.")
    Pagina<RevisionResponse> bandeja(@RequestParam(defaultValue = "PENDIENTE") String estado,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "24") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ValidacionException("size", "page >= 0 y size entre 1 y " + MAX_PAGE_SIZE + ".");
        }
        return revisiones.buscar(estado(estado), page, size).map(mapper::toRevision);
    }

    @GetMapping("/{id:\\d+}/revisiones")
    @Operation(summary = "Historial de revisiones de un producto", description = "La más reciente primero.")
    List<RevisionResponse> historial(@PathVariable long id) {
        return revisiones.deProducto(id).stream().map(mapper::toRevision).toList();
    }

    @PostMapping("/revisiones/{revisionId:\\d+}/aprobacion")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Aprobar (solo ADMIN), tal cual o con cambios",
            description = "Sin cuerpo: ALTA aprueba y publica el producto; EDICION aplica los datos propuestos "
                    + "(review-outdated 409 si el producto cambió después de proponerla). "
                    + "Con cuerpo {datos, version} (\"aprobar con cambios\"): el producto queda exactamente con datos "
                    + "(incluido publicado), con las reglas de ADMIN; version es la de actual.version que vio el ADMIN "
                    + "(version-conflict 409 si cambió). Una edición desactualizada sí se puede aprobar con cambios. "
                    + "Otros errores: review-not-pending (409), validation (400), barcode-taken (409).")
    RevisionResponse aprobar(@PathVariable long revisionId,
                             @Valid @RequestBody(required = false) AprobacionRequest req,
                             @AuthenticationPrincipal UsuarioActual usuario) {
        CambiosAprobacion cambios = req == null ? null : new CambiosAprobacion(mapper.toDatos(req.datos()), req.version());
        return mapper.toRevision(revisiones.aprobar(revisionId, cambios, usuario.id()));
    }

    @PostMapping("/revisiones/{revisionId:\\d+}/rechazo")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Rechazar con motivo (solo ADMIN)",
            description = "ALTA: el producto queda RECHAZADO hasta que el vendedor lo corrija (al editarlo vuelve a "
                    + "PENDIENTE). EDICION: la propuesta se descarta y el producto no cambia. "
                    + "Errores: review-not-pending (409).")
    RevisionResponse rechazar(@PathVariable long revisionId, @Valid @RequestBody RechazoRequest req,
                              @AuthenticationPrincipal UsuarioActual usuario) {
        return mapper.toRevision(revisiones.rechazar(revisionId, req.motivo(), usuario.id()));
    }

    private static EstadoRevision estado(String valor) {
        if (valor.equalsIgnoreCase("todas")) {
            return null;
        }
        try {
            return EstadoRevision.valueOf(valor.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidacionException("estado", "Valor no permitido: " + valor);
        }
    }
}
