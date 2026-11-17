package cl.primerplayer.caja.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Puerto de salida: {@code cierre_caja} y {@code cierre_caja_version}. Requiere transacción para escribir. */
public interface CierreRepositorio {

    Optional<Fila> porFecha(LocalDate fecha, boolean bloquear);

    /** @return {@code false} si ya existía un cierre para esa fecha (otro proceso lo creó) */
    boolean insertar(Fila fila);

    /** Copia la fila actual al historial ({@code cierre_caja_version}) y la reemplaza con {@code nueva}. */
    void reemplazar(Fila anterior, Fila nueva);

    List<Fila> pagina(int page, int size);

    long contar();

    record Fila(LocalDate fecha, long fondoInicial, long efectivoContado, long efectivoNeto, long esperadoEnCaja,
                long totalVentas, int numeroVentas, long diferencia, Map<String, Long> subtotales,
                String observaciones, int version, long cerradoPor, Instant cerradoEn) {}
}
