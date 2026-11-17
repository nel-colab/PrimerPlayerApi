package cl.primerplayer.inventario.api;

import java.util.List;

/**
 * Puerto de entrada para los módulos que venden ({@code checkout}, {@code pos}). Ambos métodos exigen una
 * transacción abierta por quien orquesta (propagation MANDATORY): stock, pedido y pago se confirman juntos.
 */
public interface InventarioOperaciones {

    /**
     * Descuenta stock de forma atómica ({@code UPDATE ... WHERE stock >= :q}), en orden de {@code productoId}
     * para evitar interbloqueos, y registra cada salida en el kardex.
     *
     * @param referencia código del pedido (ej. {@code PP-2026-000123})
     * @throws StockInsuficienteException {@code out-of-stock} / {@code stock-limit}, con la línea en {@code errors[]}
     * @throws cl.primerplayer.shared.api.RecursoNoEncontradoException si un producto no existe o está eliminado
     */
    void descontar(List<LineaStock> lineas, TipoMovimiento tipo, String referencia);

    /** Devuelve stock (anulación, pago rechazado, reserva expirada). Aplica aunque el producto esté eliminado. */
    void reponer(List<LineaStock> lineas, TipoMovimiento tipo, String referencia);

    record LineaStock(long productoId, int cantidad) {
        public LineaStock {
            if (cantidad < 1) throw new IllegalArgumentException("cantidad debe ser >= 1");
        }
    }
}
