package cl.primerplayer.inventario.application.port.out;

import java.util.Optional;

/**
 * Puerto de salida: escritura atómica de {@code producto.stock}. Ninguna implementación puede leer, calcular
 * en Java y guardar: cada método es un único {@code UPDATE ... RETURNING stock}. Requieren transacción.
 */
public interface StockRepositorio {

    /** {@code stock - cantidad} si el producto está activo y alcanza; vacío si no. */
    Optional<Integer> descontar(long productoId, int cantidad);

    /** {@code stock + delta} (delta con signo) si el producto está activo y el resultado no es negativo. */
    Optional<Integer> ajustar(long productoId, int delta);

    /** Reemplaza el stock con bloqueo de fila y devuelve ambos valores para registrar el kardex. */
    Optional<CambioStock> reemplazar(long productoId, int stock);

    /** {@code stock + cantidad} aunque el producto esté eliminado (devoluciones). Vacío si no existe. */
    Optional<Integer> reponer(long productoId, int cantidad);

    /** Stock de un producto activo (para explicar un error). Vacío si no existe o está eliminado. */
    Optional<Integer> stockActivo(long productoId);

    /** Existe el producto, aunque esté eliminado. */
    boolean existe(long productoId);

    record CambioStock(int anterior, int actual) {}
}
