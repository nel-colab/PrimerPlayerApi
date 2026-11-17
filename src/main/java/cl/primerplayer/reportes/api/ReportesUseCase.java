package cl.primerplayer.reportes.api;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Puerto de entrada: reportes de ventas (solo ventas válidas: PAGADO, LISTO_RETIRO, ENTREGADO). */
public interface ReportesUseCase {

    ReporteDia dia(LocalDate fecha);

    /** 24 tramos (0..23, hora de Chile). */
    List<Tramo> porHora(LocalDate fecha);

    ReportePeriodo porDia(YearMonth mes);

    ReportePeriodo porMes(int anio);

    /**
     * @param variacion % contra el día anterior (una decimal); {@code null} si el día anterior no tuvo ventas
     * @param porMetodo en pago mixto cada método aporta solo su parte; las ventas web aparecen como {@code webpay}
     */
    record ReporteDia(LocalDate fecha, long total, long totalDiaAnterior, Double variacion, int ordenes,
                      long ticketPromedio, long montoProductos, long montoEventos, long totalTienda, long totalWeb,
                      List<PorMetodo> porMetodo, List<Top> top5) {}

    /** @param tramo hora (0..23), día del mes (1..31) o mes (1..12) */
    record Tramo(int tramo, int ordenes, long monto) {}

    record ReportePeriodo(String periodo, long total, int ordenes, long ticketPromedio, List<Tramo> tramos,
                          List<PorMetodo> porMetodo, List<Top> top5) {}

    /** @param ordenes ventas que usaron el método (una venta mixta cuenta en cada método que usó) */
    record PorMetodo(String metodo, int ordenes, long monto) {}

    /** Top por unidades; {@code monto} = lo vendido de ese ítem. */
    record Top(String tipo, long itemId, String nombre, int unidades, long monto) {}
}
