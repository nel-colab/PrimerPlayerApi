package cl.primerplayer.inventario.web.dto;

/** Contenedor de los DTO de inventario. */
public final class InventarioDtos {

    private InventarioDtos() {}

    public record AjusteStockRequest(
            @io.swagger.v3.oas.annotations.media.Schema(example = "10", description = "positivo suma, negativo resta")
            @jakarta.validation.constraints.NotNull
            @jakarta.validation.constraints.Min(-9999) @jakarta.validation.constraints.Max(9999) Integer delta,
            @io.swagger.v3.oas.annotations.media.Schema(example = "Ingreso de mercadería, factura 1234")
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200) String motivo) {}

    public record StockResponse(long productoId, int stock) {}

    public record ConfiguracionRequest(
            @jakarta.validation.constraints.NotNull
            @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(9999) Integer umbralStockBajo) {}

    public record ConfiguracionResponse(int umbralStockBajo) {}
}
