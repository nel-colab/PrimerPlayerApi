package cl.primerplayer.inventario.domain;

import java.time.Instant;

import cl.primerplayer.inventario.api.TipoMovimiento;
import cl.primerplayer.shared.api.ValidacionException;

/** Línea del kardex. Modelo puro. {@code cantidad} negativa = salida. */
public record MovimientoStock(Long id, long productoId, TipoMovimiento tipo, int cantidad, int stockResultante,
                              String referencia, String motivo, Long usuarioId, Instant creadoEn) {

    public static final int MAX_DELTA_AJUSTE = 9_999;

    public static MovimientoStock salida(long productoId, int cantidad, TipoMovimiento tipo, String referencia,
                                         int stockResultante, Instant ahora) {
        return new MovimientoStock(null, productoId, tipo, -cantidad, stockResultante, referencia, null, null, ahora);
    }

    public static MovimientoStock entrada(long productoId, int cantidad, TipoMovimiento tipo, String referencia,
                                          int stockResultante, Instant ahora) {
        return new MovimientoStock(null, productoId, tipo, cantidad, stockResultante, referencia, null, null, ahora);
    }

    public static MovimientoStock ajuste(long productoId, int delta, String motivo, long usuarioId,
                                         int stockResultante, Instant ahora) {
        return new MovimientoStock(null, productoId, TipoMovimiento.AJUSTE, delta, stockResultante, null,
                motivo.strip(), usuarioId, ahora);
    }

    /** Reglas del ajuste manual: delta distinto de 0, acotado, y motivo obligatorio. */
    public static void validarAjuste(int delta, String motivo) {
        if (delta == 0 || Math.abs(delta) > MAX_DELTA_AJUSTE) {
            throw new ValidacionException("delta", "Debe ser distinto de 0 y estar entre -" + MAX_DELTA_AJUSTE
                    + " y " + MAX_DELTA_AJUSTE + ".");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new ValidacionException("motivo", "El motivo es obligatorio.");
        }
    }

    /** El stock pasó de 0 a más de 0 con este movimiento. */
    public boolean repuso() {
        return cantidad > 0 && stockResultante - cantidad == 0;
    }
}
