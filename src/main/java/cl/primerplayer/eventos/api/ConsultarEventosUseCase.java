package cl.primerplayer.eventos.api;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import cl.primerplayer.shared.api.Pagina;

/** Puerto de entrada: lecturas de la agenda. */
public interface ConsultarEventosUseCase {

    /** Admin: incluye no publicados; nunca eliminados. */
    EventoVista obtenerAdmin(long id);

    Pagina<EventoVista> buscarAdmin(Filtro filtro);

    /** Público: solo publicados, no eliminados. */
    EventoVista obtenerPublico(long id);

    /** Público: publicados y no eliminados; por defecto desde ahora (próximos encuentros), ordenados por fecha. */
    Pagina<EventoVista> buscarPublicos(Filtro filtro);

    /** Bloques ocupados de la agenda (para elegir horario al solicitar un cumpleaños). Sin datos personales. */
    List<BloqueOcupado> ocupados(Instant desde, Instant hasta);

    /** {@code desde}/{@code hasta} filtran por inicio; {@code null} = sin límite. */
    record Filtro(Set<TipoEvento> tipos, Instant desde, Instant hasta, int page, int size) {
        public Filtro {
            tipos = tipos == null ? Set.of() : Set.copyOf(tipos);
        }
    }

    /** {@code cumpleanos = true}: la tienda está cerrada al público en ese bloque. */
    record BloqueOcupado(Instant inicio, Instant fin, TipoEvento tipo, boolean cumpleanos) {}
}
