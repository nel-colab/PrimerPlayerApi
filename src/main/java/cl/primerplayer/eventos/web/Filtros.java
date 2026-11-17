package cl.primerplayer.eventos.web;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import cl.primerplayer.eventos.api.ConsultarEventosUseCase.Filtro;
import cl.primerplayer.eventos.api.TipoEvento;
import cl.primerplayer.shared.api.FechasTienda;
import cl.primerplayer.shared.api.ValidacionException;

final class Filtros {

    static final int MAX_PAGE_SIZE = 100;

    private Filtros() {}

    static Filtro de(List<String> tipos, String desde, String hasta, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ValidacionException("size", "page >= 0 y size entre 1 y " + MAX_PAGE_SIZE + ".");
        }
        Set<TipoEvento> t = EnumSet.noneOf(TipoEvento.class);
        if (tipos != null) {
            tipos.forEach(s -> t.add(TipoEvento.desdeSlug(s.strip())));
        }
        return new Filtro(t, FechasTienda.parse("desde", desde), FechasTienda.parse("hasta", hasta), page, size);
    }
}
