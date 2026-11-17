package cl.primerplayer.pos.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.primerplayer.pagos.api.PagosMostrador.PagoMostrador;
import cl.primerplayer.pos.api.PosUseCase;
import cl.primerplayer.pos.web.dto.ValidacionPosResponse;
import cl.primerplayer.pos.web.dto.VentaPosRequest;
import cl.primerplayer.shared.api.UsuarioActual;
import cl.primerplayer.shared.api.ValidacionException;
import cl.primerplayer.ventas.api.PedidoResponse;
import cl.primerplayer.ventas.api.VentaWebUseCase.LineaCarrito;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Venta en mostrador (VENDEDOR o ADMIN; anular solo ADMIN). */
@RestController
@RequestMapping("/api/v1/admin/pos")
@PreAuthorize("hasAnyRole('VENDEDOR','ADMIN')")
@RequiredArgsConstructor
@Tag(name = "POS (mostrador)")
@SecurityRequirement(name = "bearer")
class PosController {

    private final PosUseCase pos;

    @PostMapping("/validacion")
    @Operation(summary = "Revisar la venta antes de cobrar (precios, stock y cupos; sin límite por cliente)",
            description = "Siempre 200: los problemas por línea vienen en errors[].")
    ValidacionPosResponse validar(@Valid @RequestBody VentaPosRequest req) {
        var v = pos.validar(lineas(req));
        return new ValidacionPosResponse(
                v.lineas().stream().map(l -> new ValidacionPosResponse.Linea(l.tipo(), l.itemId(), l.nombre(),
                        l.cantidad(), l.precioUnitario(), l.subtotal(), l.disponible())).toList(),
                v.total(), v.valido(), v.errores());
    }

    @PostMapping("/ventas")
    @Operation(summary = "Cobrar una venta de mostrador (pago mixto)",
            description = "Todo en una transacción: si un pago no cuadra, no se descuenta stock. Errores del carrito "
                    + "(409/422 con errors[] por línea) y de pago (422): payments-mismatch, receipt-required, "
                    + "transfer-proof-required, tcg-card-required, cash-change-mismatch (reenviar con "
                    + "confirmarVueltoDistinto=true para aceptarlo). idempotency-conflict (409).")
    ResponseEntity<PedidoResponse> vender(
            @Valid @RequestBody VentaPosRequest req,
            @Parameter(description = "UUID único por venta; reenviarlo con el mismo cuerpo devuelve la misma venta")
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UsuarioActual vendedor) {
        var venta = pos.vender(vendedor.id(), lineas(req), pagos(req),
                Boolean.TRUE.equals(req.confirmarVueltoDistinto()), clave(idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/admin/pedidos?codigo=" + venta.codigo()))
                .body(PedidoResponse.de(venta));
    }

    @PostMapping("/ventas/{codigo}/anulacion")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Anular una venta de mostrador (solo ADMIN)",
            description = "Repone stock y cupos. Los pagos quedan registrados: el dinero se devuelve en el mostrador. "
                    + "404 item-unavailable; 409 order-invalid-state si no es una venta de mostrador vigente.")
    PedidoResponse anular(@PathVariable String codigo) {
        return PedidoResponse.de(pos.anular(codigo));
    }

    private static List<LineaCarrito> lineas(VentaPosRequest req) {
        return req.lineas().stream().map(l -> new LineaCarrito(l.type(), l.itemId(), l.quantity())).toList();
    }

    private static List<PagoMostrador> pagos(VentaPosRequest req) {
        if (req.pagos() == null) {
            return List.of();
        }
        return req.pagos().stream().map(p -> {
            var d = p.details();
            var c = d == null ? null : d.tcgCard();
            return new PagoMostrador(p.method(), p.amount(),
                    d == null ? null : d.receiptNumber(), d == null ? null : d.transferProofId(),
                    c == null ? null : c.name(), c == null ? null : c.code(), c == null ? null : c.value(),
                    d == null ? null : d.cashReceived(), d == null ? null : d.changeGiven());
        }).toList();
    }

    private static UUID clave(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ValidacionException("Idempotency-Key", "Falta el encabezado Idempotency-Key (un UUID por venta).");
        }
        try {
            return UUID.fromString(valor.strip());
        } catch (IllegalArgumentException e) {
            throw new ValidacionException("Idempotency-Key", "Idempotency-Key debe ser un UUID.");
        }
    }
}
