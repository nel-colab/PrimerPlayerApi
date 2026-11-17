package cl.primerplayer;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

import cl.primerplayer.shared.api.FechasTienda;

/**
 * Días futuros distintos para cada prueba de agenda: todas las clases comparten la misma base (Testcontainers)
 * y un bloque de una prueba no debe chocar con el de otra.
 */
public final class DiasDePrueba {

    private static final LocalDate BASE = LocalDate.now(FechasTienda.ZONA).plusYears(1);
    private static final AtomicInteger SIGUIENTE = new AtomicInteger();

    private DiasDePrueba() {}

    public static LocalDate siguiente() {
        return BASE.plusDays(SIGUIENTE.getAndIncrement());
    }

    /** {@code "2027-11-14T15:00"}: hora local de la tienda, como la envía un datetime-local. */
    public static String a(LocalDate dia, int hora) {
        return dia.atTime(LocalTime.of(hora, 0)).toString();
    }
}
