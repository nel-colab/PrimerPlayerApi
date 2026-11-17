package cl.primerplayer.inventario.api;

/** Puerto de entrada: ajuste manual de stock desde el mantenedor (VENDEDOR o ADMIN). */
public interface AjustarStockUseCase {

    /**
     * Suma {@code delta} (positivo o negativo) de forma atómica. Exige motivo y queda en el kardex como AJUSTE.
     *
     * @throws StockInsuficienteException si el resultado sería negativo
     * @throws cl.primerplayer.shared.api.RecursoNoEncontradoException si el producto no existe o está eliminado
     */
    StockActual ajustar(AjusteStock ajuste);

    record AjusteStock(long productoId, int delta, String motivo, long usuarioId) {}

    record StockActual(long productoId, int stock) {}
}
