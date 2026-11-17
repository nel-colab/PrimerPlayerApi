package cl.primerplayer.eventos.api;

import java.time.Instant;
import java.util.List;

/**
 * Evento para leer (admin y público). En la vista pública nunca hay datos del cliente de un cumpleaños.
 *
 * @param cuposDisponibles {@code cuposTotales - cuposOcupados}; {@code null} para cumpleaños
 */
public record EventoVista(
        long id,
        String titulo,
        TipoEvento tipo,
        Instant inicio,
        Instant fin,
        String lugar,
        long precio,
        Integer cuposTotales,
        int cuposOcupados,
        Integer cuposDisponibles,
        Integer limitePorCliente,
        String descripcion,
        List<String> consideraciones,
        String imagenUrl,
        boolean publicado,
        int version) {
}
