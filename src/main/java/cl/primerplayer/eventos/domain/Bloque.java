package cl.primerplayer.eventos.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Intervalo semiabierto {@code [inicio, fin)}: un bloque puede empezar justo cuando termina otro. */
public record Bloque(Instant inicio, Instant fin) {

    public Bloque {
        Objects.requireNonNull(inicio);
        Objects.requireNonNull(fin);
        if (!fin.isAfter(inicio)) {
            throw new IllegalArgumentException("fin debe ser posterior a inicio");
        }
    }

    public Duration duracion() {
        return Duration.between(inicio, fin);
    }

    public boolean seTraslapaCon(Bloque otro) {
        return inicio.isBefore(otro.fin) && otro.inicio.isBefore(fin);
    }
}
