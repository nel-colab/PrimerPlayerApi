package cl.primerplayer.ventas.web;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Locale;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import cl.primerplayer.shared.api.FechasTienda;
import cl.primerplayer.shared.api.ValidacionException;
import cl.primerplayer.ventas.api.CanalPedido;
import cl.primerplayer.ventas.api.EstadoPedido;
import cl.primerplayer.ventas.api.InscripcionesVentaUseCase.InscripcionCliente;
import cl.primerplayer.ventas.api.InscripcionesVentaUseCase.InscritosEvento;
import cl.primerplayer.ventas.api.PedidoResponse;
import cl.primerplayer.ventas.api.PedidoVista;
import cl.primerplayer.ventas.web.dto.InscripcionResponse;
import cl.primerplayer.ventas.web.dto.InscritosResponse;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface VentasWebMapper {

    /** Definición única en {@link PedidoResponse#de} (la comparten mis pedidos, el panel y el POS). */
    default PedidoResponse toResponse(PedidoVista pedido) {
        return PedidoResponse.de(pedido);
    }

    default InscripcionResponse toInscripcion(InscripcionCliente c) {
        var i = c.inscripcion();
        return new InscripcionResponse(i.id(), i.eventoId(), i.tipo().slug(), i.titulo(), enZona(i.fecha()), i.lugar(),
                i.precio(), i.cantidad(), i.precio() * i.cantidad(), i.estado().name(), c.codigoPedido(),
                enZona(i.creadaEn()), enZona(i.canceladaEn()), i.cancelable());
    }

    default InscritosResponse toInscritos(InscritosEvento e) {
        return new InscritosResponse(e.eventoId(), e.cuposConfirmados(), e.cuposReservados(),
                e.inscritos().stream().map(x -> new InscritosResponse.Registration(x.inscripcion().id(),
                        x.clienteNombre(), x.clienteEmail(), x.inscripcion().cantidad(), x.inscripcion().estado().name(),
                        x.codigoPedido(), x.canal() == null ? null : x.canal().valor(), enZona(x.inscripcion().creadaEn()),
                        enZona(x.inscripcion().canceladaEn()))).toList());
    }

    default OffsetDateTime enZona(Instant instante) {
        return FechasTienda.enZona(instante);
    }

    default String canal(CanalPedido canal) {
        return canal.valor();
    }

    default String estado(EstadoPedido estado) {
        return estado.name();
    }

    /** Acepta {@code LISTO_RETIRO} o {@code listo-retiro}. */
    static EstadoPedido estadoDe(String campo, String valor) {
        try {
            return EstadoPedido.valueOf(valor.strip().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new ValidacionException(campo, "Estado de pedido desconocido: " + valor);
        }
    }
}
