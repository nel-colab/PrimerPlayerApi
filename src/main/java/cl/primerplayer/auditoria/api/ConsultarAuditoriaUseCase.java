package cl.primerplayer.auditoria.api;

import java.time.Instant;

import cl.primerplayer.shared.api.Pagina;

/** Puerto de entrada: consulta del registro de auditoría (solo ADMIN). */
public interface ConsultarAuditoriaUseCase {

    /**
     * Más recientes primero. Todos los filtros son opcionales.
     *
     * @param hasta exclusivo
     */
    Pagina<Registro> buscar(String entidad, String entidadId, Instant desde, Instant hasta, int page, int size);

    /** @param antes / despues JSON tal como se guardó ({@code null} si no aplica) */
    record Registro(long id, Instant ocurridoEn, Long usuarioId, String usuarioNombre, String accion, String entidad,
                    String entidadId, String antes, String despues, String ip) {}
}
