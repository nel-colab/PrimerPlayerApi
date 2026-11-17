package cl.primerplayer.catalogo.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import cl.primerplayer.catalogo.api.ConsultarProductosAdminUseCase;
import cl.primerplayer.catalogo.web.dto.ProductoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Lectura por código de barras para el mostrador. La pistola lectora es un teclado: escribe el código donde está el
 * foco y presiona Enter. Para la API es lo mismo que tipearlo a mano: un GET común por código.
 */
@RestController
@PreAuthorize("hasAnyRole('VENDEDOR','ADMIN')")
@RequiredArgsConstructor
@Tag(name = "POS (mostrador)")
@SecurityRequirement(name = "bearer")
class PosProductosController {

    private final ConsultarProductosAdminUseCase consultar;
    private final CatalogoWebMapper mapper;

    @GetMapping("/api/v1/admin/pos/productos/codigo/{codigo}")
    @Operation(summary = "Buscar un producto por código de barras (pistola lectora o tipeo)",
            description = "Coincidencia exacta; solo productos publicados y no eliminados. Incluye stock para avisar "
                    + "si está agotado. 404 item-unavailable si no hay uno a la venta con ese código.")
    ResponseEntity<ProductoResponse> porCodigo(@PathVariable String codigo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())   // el stock cambia con cada venta
                .body(mapper.toResponse(consultar.porCodigoBarras(codigo)));
    }
}
