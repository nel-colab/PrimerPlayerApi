package cl.primerplayer.eventos.application.port.out;

import java.util.Optional;

import cl.primerplayer.eventos.api.ConsultarEventosUseCase.Filtro;
import cl.primerplayer.eventos.domain.Evento;
import cl.primerplayer.shared.api.Pagina;

/** Puerto de salida: persistencia de eventos. Nunca escribe {@code cupos_ocupados}. */
public interface EventoRepositorio {

    /** No eliminado (publicado o no). */
    Optional<Evento> buscarActivo(long id);

    /** Publicado y no eliminado. */
    Optional<Evento> buscarPublicado(long id);

    /** @throws cl.primerplayer.eventos.api.BloqueOcupadoException si choca con otro cumpleaños (EXCLUDE de la base) */
    Evento guardarNuevo(Evento evento);

    /**
     * @throws cl.primerplayer.shared.api.ConflictoVersionException
     * @throws cl.primerplayer.eventos.api.BloqueOcupadoException
     */
    Evento actualizar(Evento evento, int versionEsperada);

    Pagina<Evento> buscar(Filtro filtro, boolean soloPublicados);
}
