package cl.primerplayer.eventos.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Component;

import cl.primerplayer.eventos.api.SolicitudesCumpleanosUseCase.EstadoSolicitud;
import cl.primerplayer.eventos.application.port.out.SolicitudRepositorio;
import cl.primerplayer.eventos.domain.Bloque;
import cl.primerplayer.eventos.domain.SolicitudCumpleanos;
import cl.primerplayer.shared.api.Pagina;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

interface SolicitudJpaRepository extends JpaRepository<SolicitudCumpleanosEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SolicitudCumpleanosEntity s WHERE s.id = :id")
    Optional<SolicitudCumpleanosEntity> buscarParaActualizar(long id);

    List<SolicitudCumpleanosEntity> findByClienteIdOrderByCreadoEnDesc(long clienteId);

    Page<SolicitudCumpleanosEntity> findByEstado(EstadoSolicitud estado, Pageable pageable);
}

@Component
@RequiredArgsConstructor
class SolicitudPersistenceAdapter implements SolicitudRepositorio {

    private final SolicitudJpaRepository jpa;

    @Override
    public SolicitudCumpleanos guardarNueva(SolicitudCumpleanos s) {
        SolicitudCumpleanosEntity e = new SolicitudCumpleanosEntity();
        e.setClienteId(s.clienteId());
        e.setCreadoEn(s.creadoEn());
        copiar(s, e);
        return aDominio(jpa.saveAndFlush(e));
    }

    @Override
    public Optional<SolicitudCumpleanos> buscarParaResolver(long id) {
        return jpa.buscarParaActualizar(id).map(SolicitudPersistenceAdapter::aDominio);
    }

    @Override
    public void actualizar(SolicitudCumpleanos s) {
        SolicitudCumpleanosEntity e = jpa.findById(s.id()).orElseThrow();
        copiar(s, e);
        jpa.flush();
    }

    @Override
    public List<SolicitudCumpleanos> deCliente(long clienteId) {
        return jpa.findByClienteIdOrderByCreadoEnDesc(clienteId).stream().map(SolicitudPersistenceAdapter::aDominio).toList();
    }

    @Override
    public Pagina<SolicitudCumpleanos> buscar(EstadoSolicitud estado, int page, int size) {
        // Pendientes: por fecha solicitada (lo más próximo primero). Resto: lo más reciente primero.
        Sort sort = estado == EstadoSolicitud.PENDIENTE ? Sort.by("inicio").ascending() : Sort.by("creadoEn").descending();
        PageRequest pr = PageRequest.of(page, size, sort.and(Sort.by("id")));
        Page<SolicitudCumpleanosEntity> p = estado == null ? jpa.findAll(pr) : jpa.findByEstado(estado, pr);
        return Pagina.de(p.getContent().stream().map(SolicitudPersistenceAdapter::aDominio).toList(), page, size,
                p.getTotalElements());
    }

    private static void copiar(SolicitudCumpleanos s, SolicitudCumpleanosEntity e) {
        e.setInicio(s.bloque().inicio());
        e.setFin(s.bloque().fin());
        e.setFestejado(s.festejado());
        e.setInvitados((short) s.invitados());
        e.setTelefono(s.telefono());
        e.setComentario(s.comentario());
        e.setEstado(s.estado());
        e.setMotivoRechazo(s.motivoRechazo());
        e.setEventoId(s.eventoId());
        e.setResueltaPor(s.resueltaPor());
        e.setResueltaEn(s.resueltaEn());
        e.setActualizadoEn(s.resueltaEn() != null ? s.resueltaEn() : s.creadoEn());
    }

    private static SolicitudCumpleanos aDominio(SolicitudCumpleanosEntity e) {
        return new SolicitudCumpleanos(e.getId(), e.getClienteId(), new Bloque(e.getInicio(), e.getFin()),
                e.getFestejado(), e.getInvitados(), e.getTelefono(), e.getComentario(), e.getEstado(),
                e.getMotivoRechazo(), e.getEventoId(), e.getResueltaPor(), e.getResueltaEn(), e.getCreadoEn());
    }
}
