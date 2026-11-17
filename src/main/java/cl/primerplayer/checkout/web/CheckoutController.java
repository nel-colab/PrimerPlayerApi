package cl.primerplayer.checkout.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.primerplayer.checkout.api.CheckoutUseCase;
import cl.primerplayer.checkout.web.dto.CarritoRequest;
import cl.primerplayer.checkout.web.dto.CheckoutResponse;
import cl.primerplayer.checkout.web.dto.ValidacionCarritoResponse;
import cl.primerplayer.shared.api.FechasTienda;
import cl.primerplayer.shared.api.Rol;
import cl.primerplayer.shared.api.UsuarioActual;
import cl.primerplayer.shared.api.ValidacionException;
import cl.primerplayer.ventas.api.VentaWebUseCase.LineaCarrito;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Carrito y checkout")
class CheckoutController {

    private final CheckoutUseCase checkout;

    @PostMapping("/carrito/validacion")
    @Operation(summary = "Revisar el carrito (público; con sesión de cliente también evalúa su límite de compra)",
            description = "No reserva nada. Siempre 200: los problemas por línea vienen en errors[] (item-unavailable, "
                    + "out-of-stock, stock-limit, event-full, event-past, purchase-limit, invalid-quantity).")
    ValidacionCarritoResponse validar(@Valid @RequestBody CarritoRequest req,
                                      @AuthenticationPrincipal UsuarioActual usuario) {
        Long clienteId = usuario != null && usuario.rol() == Rol.CLIENTE ? usuario.id() : null;
        var v = checkout.validar(clienteId, lineas(req));
        return new ValidacionCarritoResponse(
                v.lineas().stream().map(l -> new ValidacionCarritoResponse.Linea(l.tipo(), l.itemId(), l.nombre(),
                        l.cantidad(), l.precioUnitario(), l.subtotal(), l.disponible(), l.maxPermitido())).toList(),
                v.total(), v.valido(), v.errores());
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasRole('CLIENTE')")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Crear el pedido y empezar el pago con Webpay",
            description = "Requiere sesión de cliente con correo verificado (403 email-not-verified). Reserva stock y "
                    + "cupos por 15 min. Errores del carrito con errors[] por línea; idempotency-conflict (409); "
                    + "payment-provider-error (502).")
    ResponseEntity<CheckoutResponse> checkout(
            @Valid @RequestBody CarritoRequest req,
            @Parameter(description = "UUID único por intento de compra; reenviarlo con el mismo carrito devuelve el mismo pedido")
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UsuarioActual usuario) {
        var r = checkout.iniciar(usuario.id(), lineas(req), clave(idempotencyKey));
        var pedido = new CheckoutResponse.Order(r.pedido().codigo(), r.pedido().estado().name(), r.pedido().total(),
                FechasTienda.enZona(r.pedido().expiraEn()));
        var pago = r.pago() == null ? null
                : new CheckoutResponse.Payment(r.pago().proveedor(), r.pago().url(), r.pago().token());
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/me/pedidos/" + r.pedido().codigo()))
                .body(new CheckoutResponse(pedido, pago));
    }

    private static List<LineaCarrito> lineas(CarritoRequest req) {
        return req.lineas().stream().map(l -> new LineaCarrito(l.type(), l.itemId(), l.quantity())).toList();
    }

    private static UUID clave(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ValidacionException("Idempotency-Key", "Falta el encabezado Idempotency-Key (un UUID por intento de compra).");
        }
        try {
            return UUID.fromString(valor.strip());
        } catch (IllegalArgumentException e) {
            throw new ValidacionException("Idempotency-Key", "Idempotency-Key debe ser un UUID.");
        }
    }
}
