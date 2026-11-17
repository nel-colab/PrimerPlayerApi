package cl.primerplayer.inventario.api;

import java.time.Instant;

import cl.primerplayer.shared.api.Pagina;

/** Puerto de entrada: kardex de un producto, del más reciente al más antiguo. */
public interface ConsultarKardexUseCase {

    /** @throws cl.primerplayer.shared.api.RecursoNoEncontradoException si el producto no existe */
    Pagina<Movimiento> movimientos(long productoId, int page, int size);

    /** {@code cantidad} negativa = salida. */
    record Movimiento(long id, TipoMovimiento tipo, int cantidad, int stockResultante, String referencia,
                      String motivo, Long usuarioId, Instant creadoEn) {}
}
