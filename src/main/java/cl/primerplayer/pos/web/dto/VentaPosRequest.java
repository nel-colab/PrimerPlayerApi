package cl.primerplayer.pos.web.dto;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Venta de mostrador. {@code pagos} usa el mismo formato que {@code Payment} del frontend:
 * {@code {method, amount, details}}. Pago mixto = varios elementos cuyos {@code amount} suman el total.
 */
public record VentaPosRequest(
        @NotNull @Valid List<Linea> lineas,
        @Valid List<Pago> pagos,
        @Schema(description = "true para aceptar un vuelto distinto al calculado (tras la advertencia cash-change-mismatch)")
        Boolean confirmarVueltoDistinto) {

    public record Linea(
            @Schema(example = "product", description = "product | event") @NotNull String type,
            @Schema(example = "42") @NotNull Long itemId,
            @Schema(example = "1") @NotNull Integer quantity) {}

    public record Pago(
            @Schema(example = "efectivo", description = "efectivo | debito | credito | prepago | transferencia | "
                    + "cartas-tcg | nota-credito") @NotNull String method,
            @Schema(example = "10000", description = "Parte del total cubierta con este pago (CLP)") @NotNull Long amount,
            @Valid Detalles details) {}

    public record Detalles(
            @Schema(description = "débito, crédito y prepago (obligatorio)") String receiptNumber,
            @Schema(description = "transferencia (obligatorio): id del comprobante ya subido") UUID transferProofId,
            @Schema(description = "cartas-tcg (obligatorio)") @Valid Carta tcgCard,
            @Schema(description = "efectivo (obligatorio): billetes recibidos") Long cashReceived,
            @Schema(description = "efectivo: vuelto entregado; si falta se usa el calculado") Long changeGiven) {}

    public record Carta(String name, String code, Long value) {}
}
