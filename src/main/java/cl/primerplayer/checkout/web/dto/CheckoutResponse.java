package cl.primerplayer.checkout.web.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta del checkout. Si {@code payment} viene, el navegador debe hacer un POST (formulario) a {@code payment.url}
 * con el campo {@code token_ws = payment.token}. Si es {@code null}, el pedido ya quedó pagado (total 0).
 */
public record CheckoutResponse(Order order, Payment payment) {

    public record Order(
            @Schema(example = "PP-2026-000123") String code,
            @Schema(example = "PENDIENTE_PAGO") String status,
            long total,
            @Schema(description = "Hasta cuándo se reservan stock y cupos; null si ya está pagado") OffsetDateTime expiresAt) {}

    public record Payment(
            @Schema(example = "webpay") String provider,
            @Schema(example = "https://webpay3gint.transbank.cl/webpayserver/initTransaction") String url,
            String token) {}
}
