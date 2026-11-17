package cl.primerplayer.eventos.api;

import java.time.Instant;
import java.util.List;

/**
 * Datos editables de un evento (formulario "Nuevo evento").
 *
 * @param fin           obligatorio para cumpleaños; opcional para el resto
 * @param cuposTotales  {@code null} para cumpleaños (no vende cupos)
 * @param imagenUrl     URL {@code https://}. TODO: {@code imagenArchivoId} cuando exista el módulo media.
 */
public record DatosEvento(
        String titulo,
        TipoEvento tipo,
        Instant inicio,
        Instant fin,
        String lugar,
        long precio,
        Integer cuposTotales,
        Integer limitePorCliente,
        String descripcion,
        List<String> consideraciones,
        String imagenUrl,
        boolean publicado) {

    public DatosEvento {
        consideraciones = consideraciones == null ? List.of() : List.copyOf(consideraciones);
    }
}
