package cl.primerplayer.ventas.web.dto;

import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Inscritos de un evento para la tienda: vigentes primero (por orden de inscripción); cancelados y expirados al final. */
public record InscritosResponse(
        long eventId,
        @Schema(description = "Cupos pagados (suma de quantity CONFIRMADA)") int confirmedSeats,
        @Schema(description = "Cupos reservados esperando pago (RESERVADA)") int reservedSeats,
        List<Registration> registrations) {

    public record Registration(
            long id,
            @Schema(description = "Nombre del cliente; 'Venta en mostrador' para ventas anónimas del POS") String customerName,
            String email,
            int quantity,
            @Schema(example = "CONFIRMADA") String status,
            String orderCode,
            @Schema(example = "web", description = "web | tienda") String channel,
            OffsetDateTime reservedAt,
            OffsetDateTime cancelledAt) {}
}
