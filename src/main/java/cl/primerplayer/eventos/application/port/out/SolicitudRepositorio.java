package cl.primerplayer.eventos.application.port.out;

import java.util.List;
import java.util.Optional;

import cl.primerplayer.eventos.api.SolicitudesCumpleanosUseCase.EstadoSolicitud;
import cl.primerplayer.eventos.domain.SolicitudCumpleanos;
import cl.primerplayer.shared.api.Pagina;

public interface SolicitudRepositorio {

    SolicitudCumpleanos guardarNueva(SolicitudCumpleanos solicitud);

    /** Bloquea la fila ({@code SELECT ... FOR UPDATE}) para resolverla una sola vez. */
    Optional<SolicitudCumpleanos> buscarParaResolver(long id);

    void actualizar(SolicitudCumpleanos solicitud);

    List<SolicitudCumpleanos> deCliente(long clienteId);

    Pagina<SolicitudCumpleanos> buscar(EstadoSolicitud estado, int page, int size);
}
