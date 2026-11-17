package cl.primerplayer.eventos.api;

import cl.primerplayer.shared.api.Rol;

/** Puerto de entrada: mantenedor de eventos (VENDEDOR o ADMIN). */
public interface AdministrarEventosUseCase {

    /**
     * @throws BloqueOcupadoException si choca con un cumpleaños (o, si es cumpleaños, con cualquier evento)
     */
    EventoVista crear(DatosEvento datos);

    /**
     * @throws cl.primerplayer.shared.api.ConflictoVersionException  versión distinta a {@code versionEsperada}
     * @throws cl.primerplayer.shared.api.LimiteSoloAdminException   VENDEDOR cambia un límite ya configurado
     * @throws BloqueOcupadoException
     */
    EventoVista actualizar(long id, int versionEsperada, DatosEvento datos, Rol rol);

    EventoVista cambiarPublicacion(long id, boolean publicado);

    /** Borrado lógico. Libera el bloque si era un cumpleaños. */
    void eliminar(long id);
}
