package cl.primerplayer.eventos.infrastructure.persistence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import cl.primerplayer.eventos.api.BloqueOcupadoException;
import cl.primerplayer.eventos.api.ConsultarEventosUseCase.Filtro;
import cl.primerplayer.eventos.api.DatosEvento;
import cl.primerplayer.eventos.api.TipoEvento;
import cl.primerplayer.eventos.application.port.out.EventoRepositorio;
import cl.primerplayer.eventos.domain.Evento;
import cl.primerplayer.shared.api.ConflictoVersionException;
import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.ValidacionException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

interface EventoJpaRepository extends JpaRepository<EventoEntity, Long>, JpaSpecificationExecutor<EventoEntity> {

    Optional<EventoEntity> findByIdAndEliminadoEnIsNull(long id);

    Optional<EventoEntity> findByIdAndEliminadoEnIsNullAndPublicadoTrue(long id);
}

/** Adaptador de salida: implementa {@link EventoRepositorio} con JPA. */
@Component
@RequiredArgsConstructor
class EventoPersistenceAdapter implements EventoRepositorio {

    private final EventoJpaRepository jpa;

    @Override
    public Optional<Evento> buscarActivo(long id) {
        return jpa.findByIdAndEliminadoEnIsNull(id).map(EventoPersistenceAdapter::aDominio);
    }

    @Override
    public Optional<Evento> buscarPublicado(long id) {
        return jpa.findByIdAndEliminadoEnIsNullAndPublicadoTrue(id).map(EventoPersistenceAdapter::aDominio);
    }

    @Override
    public Evento guardarNuevo(Evento evento) {
        EventoEntity e = new EventoEntity();
        e.setCreadoEn(evento.actualizadoEn());
        copiar(evento, e);
        guardar(e);
        return aDominio(e);
    }

    @Override
    public Evento actualizar(Evento evento, int versionEsperada) {
        EventoEntity e = jpa.findById(evento.id()).orElseThrow(ConflictoVersionException::new);
        if (e.getVersion() != versionEsperada) {
            throw new ConflictoVersionException();
        }
        copiar(evento, e);
        guardar(e);
        return aDominio(e);
    }

    private void guardar(EventoEntity e) {
        try {
            jpa.saveAndFlush(e);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ConflictoVersionException();
        } catch (DataIntegrityViolationException ex) {
            String msg = String.valueOf(ex.getMostSpecificCause().getMessage());
            if (msg.contains("ex_evento_cumpleanos_traslape")) {
                throw new BloqueOcupadoException();
            }
            if (msg.contains("ck_evento_cupos")) {   // carrera: se vendieron cupos mientras se bajaba el total
                throw new ValidacionException("cuposTotales", "No puede ser menor que los cupos ya vendidos.");
            }
            throw ex;
        }
    }

    @Override
    public Pagina<Evento> buscar(Filtro f, boolean soloPublicados) {
        Specification<EventoEntity> spec = (root, query, cb) -> {
            List<Predicate> y = new ArrayList<>();
            y.add(cb.isNull(root.get("eliminadoEn")));
            if (soloPublicados) {
                y.add(cb.isTrue(root.get("publicado")));
            }
            if (!f.tipos().isEmpty()) {
                y.add(root.get("tipo").in(f.tipos().stream().map(TipoEvento::slug).toList()));
            }
            if (f.desde() != null) {
                y.add(cb.greaterThanOrEqualTo(root.get("fechaHora"), f.desde()));
            }
            if (f.hasta() != null) {
                y.add(cb.lessThan(root.get("fechaHora"), f.hasta()));
            }
            return cb.and(y.toArray(Predicate[]::new));
        };
        Page<EventoEntity> page = jpa.findAll(spec,
                PageRequest.of(f.page(), f.size(), Sort.by("fechaHora").ascending().and(Sort.by("id"))));
        return Pagina.de(page.getContent().stream().map(EventoPersistenceAdapter::aDominio).toList(),
                f.page(), f.size(), page.getTotalElements());
    }

    private static void copiar(Evento ev, EventoEntity e) {
        DatosEvento d = ev.datos();
        e.setTitulo(d.titulo());
        e.setTipo(d.tipo().slug());
        e.setFechaHora(d.inicio());
        e.setFechaHoraFin(d.fin());
        e.setLugar(d.lugar());
        e.setPrecio(d.precio());
        e.setCuposTotales(d.cuposTotales());
        e.setLimitePorCliente(d.limitePorCliente());
        e.setDescripcion(d.descripcion());
        e.setConsideraciones(d.consideraciones().toArray(String[]::new));
        e.setImagenUrl(d.imagenUrl());
        e.setPublicado(d.publicado());
        e.setActualizadoEn(ev.actualizadoEn());
        e.setEliminadoEn(ev.eliminadoEn());
    }

    private static Evento aDominio(EventoEntity e) {
        DatosEvento d = new DatosEvento(e.getTitulo(), TipoEvento.desdeSlug(e.getTipo()), e.getFechaHora(),
                e.getFechaHoraFin(), e.getLugar(), e.getPrecio(), e.getCuposTotales(), e.getLimitePorCliente(),
                e.getDescripcion(), e.getConsideraciones() == null ? List.of() : Arrays.asList(e.getConsideraciones()),
                e.getImagenUrl(), e.isPublicado());
        return Evento.reconstituir(e.getId(), d, e.getCuposOcupados(), e.getVersion(), e.getActualizadoEn(),
                e.getEliminadoEn());
    }
}
