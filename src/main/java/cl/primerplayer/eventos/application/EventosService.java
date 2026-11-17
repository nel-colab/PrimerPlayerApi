package cl.primerplayer.eventos.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.eventos.api.AdministrarEventosUseCase;
import cl.primerplayer.eventos.api.ConsultarEventosUseCase;
import cl.primerplayer.eventos.api.DatosEvento;
import cl.primerplayer.eventos.api.EventoVista;
import cl.primerplayer.eventos.application.port.out.EventoRepositorio;
import cl.primerplayer.eventos.domain.Bloque;
import cl.primerplayer.eventos.domain.Evento;
import cl.primerplayer.shared.api.ConflictoVersionException;
import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import cl.primerplayer.shared.api.Rol;
import cl.primerplayer.shared.api.ValidacionException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class EventosService implements AdministrarEventosUseCase, ConsultarEventosUseCase {

    static final Duration MAX_RANGO_AGENDA = Duration.ofDays(92);

    private final EventoRepositorio eventos;
    private final Agenda agenda;
    private final Clock clock;

    // ---------- comandos ----------

    @Override
    @Transactional
    public EventoVista crear(DatosEvento datos) {
        agenda.bloquear();
        Evento nuevo = Evento.crear(datos, clock.instant());
        agenda.exigirLibre(nuevo, null);
        return Eventos.vista(eventos.guardarNuevo(nuevo));
    }

    @Override
    @Transactional
    public EventoVista actualizar(long id, int versionEsperada, DatosEvento datos, Rol rol) {
        agenda.bloquear();
        Evento actual = activo(id);
        if (actual.version() != versionEsperada) {
            throw new ConflictoVersionException();
        }
        Evento cambiado = actual.actualizar(datos, rol, clock.instant());
        agenda.exigirLibre(cambiado, id);
        return Eventos.vista(eventos.actualizar(cambiado, versionEsperada));
    }

    @Override
    @Transactional
    public EventoVista cambiarPublicacion(long id, boolean publicado) {
        Evento actual = activo(id);
        return Eventos.vista(eventos.actualizar(actual.conPublicacion(publicado, clock.instant()), actual.version()));
    }

    /**
     * TODO(pregunta-abierta): al eliminar un evento con cupos vendidos, ¿se anulan las inscripciones y se
     * reembolsa? Hoy solo se oculta (borrado lógico); las inscripciones quedan intactas.
     */
    @Override
    @Transactional
    public void eliminar(long id) {
        Evento actual = activo(id);
        eventos.actualizar(actual.eliminar(clock.instant()), actual.version());
    }

    // ---------- consultas ----------

    @Override
    @Transactional(readOnly = true)
    public EventoVista obtenerAdmin(long id) {
        return Eventos.vista(activo(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<EventoVista> buscarAdmin(Filtro filtro) {
        return eventos.buscar(filtro, false).map(Eventos::vista);
    }

    @Override
    @Transactional(readOnly = true)
    public EventoVista obtenerPublico(long id) {
        return eventos.buscarPublicado(id).map(Eventos::vista).orElseThrow(() -> noExiste(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<EventoVista> buscarPublicos(Filtro filtro) {
        Instant desde = filtro.desde() != null ? filtro.desde() : clock.instant();   // "próximos encuentros"
        var f = new Filtro(filtro.tipos(), desde, filtro.hasta(), filtro.page(), filtro.size());
        return eventos.buscar(f, true).map(Eventos::vista);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BloqueOcupado> ocupados(Instant desde, Instant hasta) {
        if (desde == null || hasta == null || !hasta.isAfter(desde)
                || Duration.between(desde, hasta).compareTo(MAX_RANGO_AGENDA) > 0) {
            throw new ValidacionException("hasta", "Indica desde y hasta (máximo 92 días).");
        }
        return agenda.ocupaciones(new Bloque(desde, hasta)).stream()
                .map(o -> new BloqueOcupado(o.bloque().inicio(), o.bloque().fin(), o.tipo(), !o.tipo().vendeCupos()))
                .toList();
    }

    private Evento activo(long id) {
        return eventos.buscarActivo(id).orElseThrow(() -> noExiste(id));
    }

    private static RecursoNoEncontradoException noExiste(long id) {
        return new RecursoNoEncontradoException("El evento " + id + " no existe.");
    }
}
