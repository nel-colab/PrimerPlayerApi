package cl.primerplayer.ventas.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record EstadoPedidoRequest(
        @Schema(example = "LISTO_RETIRO", description = "LISTO_RETIRO | ENTREGADO") @NotBlank String estado,
        @Schema(description = "Enviar correo al cliente al marcar LISTO_RETIRO") boolean notificarCliente) {
}
