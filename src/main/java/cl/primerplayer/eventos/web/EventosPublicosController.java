package cl.primerplayer.eventos.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.primerplayer.eventos.api.ConsultarEventosUseCase;
import cl.primerplayer.eventos.web.dto.EventoDtos.BloqueOcupadoResponse;
import cl.primerplayer.eventos.web.dto.EventoDtos.EventoResponse;
import cl.primerplayer.shared.api.FechasTienda;
import cl.primerplayer.shared.api.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Eventos (público)")
class EventosPublicosController {

    private final ConsultarEventosUseCase consultar;
    private final EventosWebMapper mapper;

    @GetMapping("/eventos")
    @Operation(summary = "Próximos encuentros (publicados), por fecha",
            description = "Sin 'desde' parte desde ahora. Los cumpleaños aparecen como bloque con tiendaCerrada=true.")
    Pagina<EventoResponse> buscar(@RequestParam(required = false) List<String> tipo,
                                  @RequestParam(required = false) String desde,
                                  @RequestParam(required = false) String hasta,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "24") int size) {
        return consultar.buscarPublicos(Filtros.de(tipo, desde, hasta, page, size)).map(mapper::toPublico);
    }

    @GetMapping("/eventos/{id}")
    EventoResponse obtener(@PathVariable long id) {
        return mapper.toPublico(consultar.obtenerPublico(id));
    }

    @GetMapping("/agenda/ocupados")
    @Operation(summary = "Bloques ocupados (para elegir horario de un cumpleaños)",
            description = "Rango máximo 92 días. Sin datos de clientes.")
    List<BloqueOcupadoResponse> ocupados(@RequestParam String desde, @RequestParam String hasta) {
        return consultar.ocupados(FechasTienda.parse("desde", desde), FechasTienda.parse("hasta", hasta)).stream()
                .map(b -> new BloqueOcupadoResponse(FechasTienda.enZona(b.inicio()), FechasTienda.enZona(b.fin()),
                        b.tipo().slug(), b.cumpleanos()))
                .toList();
    }
}
