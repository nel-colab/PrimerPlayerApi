package cl.primerplayer.ventas.web.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** Una inscripción del cliente ("mis inscripciones"). Título, fecha, lugar y precio son los del momento de la compra. */
public record InscripcionResponse(
        long id,
        long eventId,
        @Schema(example = "league-cup") String eventType,
        String eventTitle,
        OffsetDateTime eventDate,
        String eventPlace,
        @Schema(description = "CLP por cupo") long unitPrice,
        int quantity,
        @Schema(description = "unitPrice × quantity") long total,
        @Schema(example = "CONFIRMADA", description = "RESERVADA | CONFIRMADA | CANCELADA | EXPIRADA") String status,
        @Schema(example = "PP-2026-000123") String orderCode,
        OffsetDateTime reservedAt,
        OffsetDateTime cancelledAt,
        @Schema(description = "true si el cliente puede cancelarla ahora (pagada y el evento aún no comienza)") boolean cancellable) {
}
