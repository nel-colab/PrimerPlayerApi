package cl.primerplayer.eventos.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.eventos.api.DatosEvento;
import cl.primerplayer.eventos.api.SolicitudNoPendienteException;
import cl.primerplayer.eventos.api.SolicitudesCumpleanosUseCase;
import cl.primerplayer.eventos.api.TipoEvento;
import cl.primerplayer.eventos.application.port.out.EventoRepositorio;
import cl.primerplayer.eventos.application.port.out.SolicitudRepositorio;
import cl.primerplayer.eventos.domain.Evento;
import cl.primerplayer.eventos.domain.SolicitudCumpleanos;
import cl.primerplayer.eventos.events.SolicitudCumpleanosEventos.Recibida;
import cl.primerplayer.eventos.events.SolicitudCumpleanosEventos.Resuelta;
import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class SolicitudesCumpleanosService implements SolicitudesCumpleanosUseCase {

    private static final ZoneId ZONA_TIENDA = ZoneId.of("America/Santiago");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final SolicitudRepositorio solicitudes;
    private final EventoRepositorio eventos;
    private final Agenda agenda;
    private final ApplicationEventPublisher publicador;
    private final EventosProperties props;
    private final cl.primerplayer.identidad.api.ConsultarUsuarioUseCase usuarios;
    private final Clock clock;

    // ---------- cliente ----------

    @Override
    @Transactional
    public SolicitudVista solicitar(long clienteId, NuevaSolicitud n) {
        if (usuarios.estadoParaComprar(clienteId)
                == cl.primerplayer.identidad.api.ConsultarUsuarioUseCase.EstadoCompra.LISTA_NEGRA) {
            throw new cl.primerplayer.identidad.api.ListaNegraException();
        }
        var c = props.cumpleanos();
        SolicitudCumpleanos nueva = SolicitudCumpleanos.nueva(clienteId, n.inicio(), n.fin(), n.festejado(),
                n.invitados(), n.telefono(), n.comentario(),
                new SolicitudCumpleanos.Reglas(c.anticipacionMinima(), c.duracionMinima(), c.duracionMaxima()),
                clock.instant());
        // Aviso temprano: si ya está tomado no tiene sentido dejarla pendiente. La validación definitiva es al aprobar.
        agenda.exigirLibreParaCumpleanos(nueva.bloque());
        SolicitudCumpleanos guardada = solicitudes.guardarNueva(nueva);
        publicador.publishEvent(new Recibida(guardada.id(), clienteId, guardada.bloque().inicio(), guardada.bloque().fin()));
        return vista(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SolicitudVista> deCliente(long clienteId) {
        return solicitudes.deCliente(clienteId).stream().map(SolicitudesCumpleanosService::vista).toList();
    }

    @Override
    @Transactional
    public SolicitudVista cancelar(long clienteId, long solicitudId) {
        SolicitudCumpleanos s = solicitudes.buscarParaResolver(solicitudId)
                .filter(x -> x.clienteId() == clienteId)          // ajena => 404, no revela que existe
                .orElseThrow(() -> noExiste(solicitudId));
        SolicitudCumpleanos cancelada = s.cancelar(clock.instant());
        solicitudes.actualizar(cancelada);
        return vista(cancelada);
    }

    // ---------- tienda ----------

    @Override
    @Transactional(readOnly = true)
    public Pagina<SolicitudVista> buscar(EstadoSolicitud estado, int page, int size) {
        return solicitudes.buscar(estado, page, size).map(SolicitudesCumpleanosService::vista);
    }

    @Override
    @Transactional
    public SolicitudVista aprobar(long solicitudId, Aprobacion a, long usuarioId) {
        agenda.bloquear();
        SolicitudCumpleanos s = solicitudes.buscarParaResolver(solicitudId).orElseThrow(() -> noExiste(solicitudId));
        if (s.estado() != EstadoSolicitud.PENDIENTE) {
            throw new SolicitudNoPendienteException(s.estado().name());
        }
        Instant ahora = clock.instant();
        Evento evento = Evento.crear(datosDelEvento(s, a), ahora);
        agenda.exigirLibre(evento, null);
        Evento guardado = eventos.guardarNuevo(evento);   // EXCLUDE de la base: red final contra traslapes

        SolicitudCumpleanos aprobada = s.aprobar(guardado.id(), usuarioId, ahora);
        solicitudes.actualizar(aprobada);
        publicador.publishEvent(new Resuelta(aprobada.id(), aprobada.clienteId(), true, guardado.id(), null));
        return vista(aprobada);
    }

    @Override
    @Transactional
    public SolicitudVista rechazar(long solicitudId, String motivo, long usuarioId) {
        SolicitudCumpleanos s = solicitudes.buscarParaResolver(solicitudId).orElseThrow(() -> noExiste(solicitudId));
        SolicitudCumpleanos rechazada = s.rechazar(motivo, usuarioId, clock.instant());
        solicitudes.actualizar(rechazada);
        publicador.publishEvent(new Resuelta(rechazada.id(), rechazada.clienteId(), false, null,
                rechazada.motivoRechazo()));
        return vista(rechazada);
    }

    /** Evento público del bloque: anuncia el cierre sin ningún dato del cliente. */
    private DatosEvento datosDelEvento(SolicitudCumpleanos s, Aprobacion a) {
        String desde = HORA.format(s.bloque().inicio().atZone(ZONA_TIENDA));
        String hasta = HORA.format(s.bloque().fin().atZone(ZONA_TIENDA));
        String titulo = texto(a.titulo(), "Cumpleaños privado · tienda cerrada");
        String descripcion = texto(a.descripcion(),
                "La tienda estará cerrada al público de " + desde + " a " + hasta + " hrs por un evento privado.");
        boolean publicado = a.publicado() == null || a.publicado();
        return new DatosEvento(titulo, TipoEvento.CUMPLEANOS, s.bloque().inicio(), s.bloque().fin(),
                props.lugarPorDefecto(), 0, null, null, descripcion, a.consideraciones(), null, publicado);
    }

    private static String texto(String valor, String porDefecto) {
        return valor == null || valor.isBlank() ? porDefecto : valor;
    }

    private static RecursoNoEncontradoException noExiste(long id) {
        return new RecursoNoEncontradoException("La solicitud " + id + " no existe.");
    }

    static SolicitudVista vista(SolicitudCumpleanos s) {
        return new SolicitudVista(s.id(), s.clienteId(), s.bloque().inicio(), s.bloque().fin(), s.festejado(),
                s.invitados(), s.telefono(), s.comentario(), s.estado(), s.motivoRechazo(), s.eventoId(),
                s.resueltaEn(), s.creadoEn());
    }
}
