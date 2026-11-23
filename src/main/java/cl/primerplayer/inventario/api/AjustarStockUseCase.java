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

    /**
     * Reemplazo absoluto con bloqueo atómico, reservado para operaciones de importación.
     * La operación requiere que el caso de uso orquestador abra la transacción.
     */
    StockActual reemplazar(ReemplazoStock reemplazo);

    record AjusteStock(long productoId, int delta, String motivo, long usuarioId) {}

    record ReemplazoStock(long productoId, int stock, String motivo, long usuarioId) {}

    record StockActual(long productoId, int stock) {}
}
