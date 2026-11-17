package cl.primerplayer.caja.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import cl.primerplayer.shared.api.Pagina;

/** Puerto de entrada: resumen del día y cierre de caja. */
public interface CajaUseCase {

    /** Resumen del día (Chile) calculado desde las ventas válidas, más el cierre si ya existe. */
    ResumenDia resumen(LocalDate fecha);

    /**
     * Cierra (o vuelve a cerrar) la caja del día. Volver a cerrar guarda la versión anterior en el historial y sube
     * la versión. Todo se calcula en el servidor.
     *
     * @throws cl.primerplayer.shared.api.ValidacionException fecha futura, montos negativos u observaciones largas
     */
    Cierre cerrar(LocalDate fecha, long fondoInicial, long efectivoContado, String observaciones, long usuarioId);

    /** Historial de cierres, del más reciente al más antiguo. */
    Pagina<Cierre> cierres(int page, int size);

    /**
     * @param subtotales   monto por método de pago (en pago mixto, cada método aporta solo su parte)
     * @param efectivoNeto Σ(efectivo recibido − vuelto entregado) de las ventas de mostrador
     * @param totalWeb     ventas web del día (no pasan por la caja física)
     */
    record ResumenDia(LocalDate fecha, int numeroVentas, int unidades, long total, long totalTienda, long totalWeb,
                      Map<String, Long> subtotales, int pagosEfectivo, long efectivoNeto,
                      List<ProductoVendido> productos, Cierre cierre) {}

    record ProductoVendido(String tipo, long itemId, String nombre, int unidades, long monto) {}

    /** @param estado {@code cuadrada} (diferencia 0), {@code sobrante} (&gt; 0) o {@code faltante} (&lt; 0) */
    record Cierre(LocalDate fecha, long fondoInicial, long efectivoContado, long efectivoNeto, long esperadoEnCaja,
                  long diferencia, String estado, long totalVentas, int numeroVentas, Map<String, Long> subtotales,
                  String observaciones, int version, long cerradoPorId, String cerradoPor, Instant cerradoEn) {}
}
