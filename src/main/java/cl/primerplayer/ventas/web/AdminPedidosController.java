package cl.primerplayer.ventas.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.primerplayer.shared.api.FechasTienda;
import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.ventas.api.CanalPedido;
import cl.primerplayer.ventas.api.ConsultarPedidosUseCase;
import cl.primerplayer.ventas.api.ConsultarPedidosUseCase.FiltroPedidos;
import cl.primerplayer.ventas.api.EstadoPedido;
import cl.primerplayer.ventas.api.GestionarPedidosUseCase;
import cl.primerplayer.ventas.web.dto.EstadoPedidoRequest;
import cl.primerplayer.ventas.api.PedidoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Panel de pedidos de la tienda (VENDEDOR o ADMIN). */
@RestController
@RequestMapping("/api/v1/admin/pedidos")
@PreAuthorize("hasAnyRole('VENDEDOR','ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin · Pedidos")
@SecurityRequirement(name = "bearer")
class AdminPedidosController {

    private final ConsultarPedidosUseCase consultar;
    private final GestionarPedidosUseCase gestionar;
    private final VentasWebMapper mapper;

    @GetMapping
    @Operation(summary = "Buscar pedidos, más recientes primero")
    Pagina<PedidoResponse> buscar(
            @Parameter(description = "fecha (día de Chile, inclusive) o fecha-hora ISO", example = "2026-11-01")
            @RequestParam(required = false) String desde,
            @Parameter(description = "fecha (día de Chile, inclusive) o fecha-hora ISO (exclusiva)", example = "2026-11-30")
            @RequestParam(required = false) String hasta,
            @Parameter(description = "web | tienda") @RequestParam(required = false) String canal,
            @Parameter(description = "repetible (OR), p. ej. PAGADO o listo-retiro") @RequestParam(required = false) List<String> estado,
            @Parameter(description = "id del vendedor") @RequestParam(required = false) Long vendedor,
            @Parameter(description = "parte del código, p. ej. 000123") @RequestParam(required = false) String codigo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        var filtro = new FiltroPedidos(FechasTienda.parse("desde", desde), hastaExclusivo(hasta),
                canal == null || canal.isBlank() ? null : CanalPedido.de(canal), estados(estado), vendedor, codigo,
                page, size);
        return consultar.buscar(filtro).map(mapper::toResponse);
    }

    @PatchMapping("/{codigo}/estado")
    @Operation(summary = "Marcar un pedido web pagado como LISTO_RETIRO o ENTREGADO",
            description = "Errores: validation (400), item-unavailable (404), order-invalid-state (409).")
    PedidoResponse cambiarEstado(@PathVariable String codigo, @Valid @RequestBody EstadoPedidoRequest req) {
        return mapper.toResponse(gestionar.marcarRetiro(codigo, VentasWebMapper.estadoDe("estado", req.estado())));
    }

    /** Una fecha sola ({@code 2026-11-30}) incluye ese día completo: el límite es el inicio del día siguiente. */
    private static Instant hastaExclusivo(String hasta) {
        Instant instante = FechasTienda.parse("hasta", hasta);
        if (instante != null && hasta.strip().length() == 10) {
            return LocalDate.parse(hasta.strip()).plusDays(1).atStartOfDay(FechasTienda.ZONA).toInstant();
        }
        return instante;
    }

    private static Set<EstadoPedido> estados(List<String> valores) {
        if (valores == null) {
            return Set.of();
        }
        return valores.stream().filter(v -> !v.isBlank()).map(v -> VentasWebMapper.estadoDe("estado", v))
                .collect(Collectors.toUnmodifiableSet());
    }
}
