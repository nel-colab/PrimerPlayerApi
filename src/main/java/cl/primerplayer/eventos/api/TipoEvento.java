package cl.primerplayer.eventos.api;

import java.util.Arrays;

import cl.primerplayer.shared.api.ValidacionException;

/** Tipos de evento. {@link #slug()} es el valor de la base ({@code evento.tipo}) y del JSON. */
public enum TipoEvento {
    LEAGUE_CHALLENGE("league-challenge"),
    LEAGUE_CUP("league-cup"),
    TORNEO_TCG("torneo-tcg"),
    PRELANZAMIENTO("prelanzamiento"),
    /** Ediciones nuevas de cartas y lanzamientos de juegos de mesa. */
    LANZAMIENTO("lanzamiento"),
    VIDEOJUEGOS("videojuegos"),
    /** Bloque reservado por un cumpleaños: la tienda cierra al público y no se venden cupos. */
    CUMPLEANOS("cumpleanos"),
    OTROS("otros");

    private final String slug;

    TipoEvento(String slug) { this.slug = slug; }

    public String slug() { return slug; }

    public boolean vendeCupos() { return this != CUMPLEANOS; }

    public static TipoEvento desdeSlug(String slug) {
        return Arrays.stream(values()).filter(t -> t.slug.equals(slug)).findFirst()
                .orElseThrow(() -> new ValidacionException("tipo", "Tipo de evento no válido: " + slug));
    }
}
