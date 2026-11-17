package cl.primerplayer.shared.api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Conversión de fechas de la API. Se guarda siempre en UTC ({@link Instant}); la tienda opera en
 * {@code America/Santiago}.
 */
public final class FechasTienda {

    public static final ZoneId ZONA = ZoneId.of("America/Santiago");

    private FechasTienda() {}

    /**
     * Acepta ISO 8601 con zona ({@code 2026-11-14T15:00:00-03:00}, {@code ...Z}), fecha-hora local de la tienda
     * ({@code 2026-11-14T15:00}, lo que envía un {@code <input type="datetime-local">}) o solo fecha
     * ({@code 2026-11-14} = inicio de ese día en Chile).
     *
     * @return {@code null} si el valor es nulo o vacío
     * @throws ValidacionException con {@code campo} si no se puede interpretar
     */
    public static Instant parse(String campo, String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String v = valor.strip();
        try {
            if (v.length() == 10) {
                return LocalDate.parse(v).atStartOfDay(ZONA).toInstant();
            }
            if (v.endsWith("Z") || v.matches(".*[+-]\\d{2}:?\\d{2}$")) {
                return OffsetDateTime.parse(v).toInstant();
            }
            return LocalDateTime.parse(v).atZone(ZONA).toInstant();
        } catch (DateTimeParseException e) {
            throw new ValidacionException(campo, "Fecha inválida: usa ISO 8601, p. ej. 2026-11-14T15:00.");
        }
    }

    /** Para responder: el mismo instante con el desfase de Chile ({@code -03:00} o {@code -04:00}). */
    public static OffsetDateTime enZona(Instant instante) {
        return instante == null ? null : instante.atZone(ZONA).toOffsetDateTime();
    }
}
