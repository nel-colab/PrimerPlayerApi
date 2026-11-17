package cl.primerplayer.eventos.web;

import java.time.Instant;
import java.time.OffsetDateTime;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import cl.primerplayer.eventos.api.DatosEvento;
import cl.primerplayer.eventos.api.EventoVista;
import cl.primerplayer.eventos.api.SolicitudesCumpleanosUseCase.Aprobacion;
import cl.primerplayer.eventos.api.SolicitudesCumpleanosUseCase.NuevaSolicitud;
import cl.primerplayer.eventos.api.SolicitudesCumpleanosUseCase.SolicitudVista;
import cl.primerplayer.eventos.api.TipoEvento;
import cl.primerplayer.eventos.web.dto.EventoDtos.AprobacionRequest;
import cl.primerplayer.eventos.web.dto.EventoDtos.EventoRequest;
import cl.primerplayer.eventos.web.dto.EventoDtos.EventoResponse;
import cl.primerplayer.eventos.web.dto.EventoDtos.SolicitudRequest;
import cl.primerplayer.eventos.web.dto.EventoDtos.SolicitudResponse;
import cl.primerplayer.shared.api.FechasTienda;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface EventosWebMapper {

    // ---------- eventos ----------

    @Mapping(target = "fecha", source = "inicio")
    @Mapping(target = "fechaFin", source = "fin")
    @Mapping(target = "limitePorUsuario", source = "limitePorCliente")
    @Mapping(target = "imagen", source = "imagenUrl")
    @Mapping(target = "tiendaCerrada", expression = "java(!vista.tipo().vendeCupos())")
    EventoResponse toAdmin(EventoVista vista);

    /** Vista pública: sin cupos ocupados ni versión (campos internos del mantenedor). */
    default EventoResponse toPublico(EventoVista v) {
        EventoResponse r = toAdmin(v);
        return new EventoResponse(r.id(), r.titulo(), r.tipo(), r.fecha(), r.fechaFin(), r.lugar(), r.precio(),
                r.cuposTotales(), r.cuposDisponibles(), r.limitePorUsuario(), r.descripcion(), r.consideraciones(),
                r.imagen(), r.tiendaCerrada(), r.publicado(), null, null);
    }

    default DatosEvento toDatos(EventoRequest r) {
        return new DatosEvento(r.titulo(), TipoEvento.desdeSlug(r.tipo()), FechasTienda.parse("fecha", r.fecha()),
                FechasTienda.parse("fechaFin", r.fechaFin()), r.lugar(), r.precio() == null ? 0 : r.precio(),
                r.cuposTotales(), r.limitePorUsuario(), r.descripcion(), r.consideraciones(), r.imagen(),
                Boolean.TRUE.equals(r.publicado()));
    }

    // ---------- solicitudes ----------

    default NuevaSolicitud toNueva(SolicitudRequest r) {
        return new NuevaSolicitud(FechasTienda.parse("inicio", r.inicio()), FechasTienda.parse("fin", r.fin()),
                r.festejado(), r.invitados(), r.telefono(), r.comentario());
    }

    Aprobacion toAprobacion(AprobacionRequest r);

    @Mapping(target = "clienteId", source = "clienteId")
    SolicitudResponse toTienda(SolicitudVista s);

    /** El cliente no necesita su propio id en la respuesta. */
    default SolicitudResponse toCliente(SolicitudVista s) {
        SolicitudResponse r = toTienda(s);
        return new SolicitudResponse(r.id(), r.inicio(), r.fin(), r.festejado(), r.invitados(), r.telefono(),
                r.comentario(), r.estado(), r.motivoRechazo(), r.eventoId(), r.resueltaEn(), r.creadoEn(), null);
    }

    // ---------- conversiones ----------

    default String tipo(TipoEvento t) {
        return t == null ? null : t.slug();
    }

    default OffsetDateTime fecha(Instant i) {
        return FechasTienda.enZona(i);
    }
}
