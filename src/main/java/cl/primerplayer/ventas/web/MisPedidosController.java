package cl.primerplayer.ventas.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.UsuarioActual;
import cl.primerplayer.ventas.api.ConsultarPedidosUseCase;
import cl.primerplayer.ventas.api.PedidoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** "Mis pedidos": el cliente sale siempre del token, nunca del request. */
@RestController
@RequestMapping("/api/v1/me/pedidos")
@PreAuthorize("hasAnyRole('CLIENTE','VENDEDOR','ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Cliente · Pedidos")
@SecurityRequirement(name = "bearer")
class MisPedidosController {

    private final ConsultarPedidosUseCase consultar;
    private final VentasWebMapper mapper;

    @GetMapping
    @Operation(summary = "Mis pedidos, más recientes primero")
    Pagina<PedidoResponse> mios(@AuthenticationPrincipal UsuarioActual usuario,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "10") int size) {
        return consultar.delCliente(usuario.id(), page, size).map(mapper::toResponse);
    }

    @GetMapping("/{codigo}")
    @Operation(summary = "Detalle de uno de mis pedidos", description = "De otro cliente o inexistente: 404 item-unavailable.")
    PedidoResponse uno(@AuthenticationPrincipal UsuarioActual usuario, @PathVariable String codigo) {
        return mapper.toResponse(consultar.delCliente(usuario.id(), codigo));
    }
}
