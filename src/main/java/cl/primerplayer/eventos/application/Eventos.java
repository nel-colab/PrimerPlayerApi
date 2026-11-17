package cl.primerplayer.eventos.application;

import cl.primerplayer.eventos.api.DatosEvento;
import cl.primerplayer.eventos.api.EventoVista;
import cl.primerplayer.eventos.domain.Evento;

final class Eventos {

    private Eventos() {}

    static EventoVista vista(Evento e) {
        DatosEvento d = e.datos();
        return new EventoVista(e.id(), d.titulo(), d.tipo(), d.inicio(), d.fin(), d.lugar(), d.precio(),
                d.cuposTotales(), e.cuposOcupados(), e.cuposDisponibles(), d.limitePorCliente(), d.descripcion(),
                d.consideraciones(), d.imagenUrl(), d.publicado(), e.version());
    }
}
