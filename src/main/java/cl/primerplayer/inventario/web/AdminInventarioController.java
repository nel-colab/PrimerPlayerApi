package cl.primerplayer.inventario.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.primerplayer.inventario.api.AjustarStockUseCase;
import cl.primerplayer.inventario.api.AjustarStockUseCase.AjusteStock;
import cl.primerplayer.inventario.api.ConfiguracionInventarioUseCase;
import cl.primerplayer.inventario.api.ConsultarKardexUseCase;
import cl.primerplayer.inventario.api.ConsultarKardexUseCase.Movimiento;
import cl.primerplayer.inventario.web.dto.InventarioDtos.AjusteStockRequest;
import cl.primerplayer.inventario.web.dto.InventarioDtos.ConfiguracionRequest;
import cl.primerplayer.inventario.web.dto.InventarioDtos.ConfiguracionResponse;
import cl.primerplayer.inventario.web.dto.InventarioDtos.StockResponse;
import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.UsuarioActual;
import cl.primerplayer.shared.api.ValidacionException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/inventario")
@PreAuthorize("hasAnyRole('VENDEDOR','ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin · Inventario")
@SecurityRequirement(name = "bearer")
class AdminInventarioController {

    private final AjustarStockUseCase ajustarStock;
    private final ConsultarKardexUseCase kardex;
    private final ConfiguracionInventarioUseCase configuracion;
    private final InventarioWebMapper mapper;

    @PostMapping("/{productoId}/ajustes")
    @Operation(summary = "Ajustar stock (+/-) con motivo", description = "Atómico; nunca deja stock negativo. "
            + "Errores: out-of-stock / stock-limit (409), item-unavailable (404).")
    StockResponse ajustar(@PathVariable long productoId, @Valid @RequestBody AjusteStockRequest req,
                          @AuthenticationPrincipal UsuarioActual usuario) {
        return mapper.toResponse(ajustarStock.ajustar(
                new AjusteStock(productoId, req.delta(), req.motivo(), usuario.id())));
    }

    @GetMapping("/{productoId}/movimientos")
    @Operation(summary = "Kardex paginado (más reciente primero)")
    Pagina<Movimiento> movimientos(@PathVariable long productoId,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ValidacionException("size", "page >= 0 y size entre 1 y 100.");
        }
        return kardex.movimientos(productoId, page, size);
    }

    @GetMapping("/configuracion")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Umbral de stock bajo (ADMIN)")
    ConfiguracionResponse obtenerConfiguracion() {
        return new ConfiguracionResponse(configuracion.umbralStockBajo());
    }

    @PutMapping("/configuracion")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cambiar umbral de stock bajo (ADMIN)")
    ConfiguracionResponse cambiarConfiguracion(@Valid @RequestBody ConfiguracionRequest req) {
        return new ConfiguracionResponse(configuracion.cambiarUmbralStockBajo(req.umbralStockBajo()));
    }
}
