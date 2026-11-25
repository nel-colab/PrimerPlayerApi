package cl.primerplayer.catalogo.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import cl.primerplayer.catalogo.api.DatosProducto;
import cl.primerplayer.catalogo.api.RevisionesProductoUseCase.EstadoRevision;
import cl.primerplayer.catalogo.api.RevisionesProductoUseCase.TipoRevision;
import cl.primerplayer.shared.api.Pagina;

/** Puerto de salida: tabla {@code producto_revision} (a lo más una PENDIENTE por producto). */
public interface RevisionRepositorio {

    /**
     * @param propuesto datos propuestos (EDICION) o {@code null} (ALTA)
     * @throws cl.primerplayer.shared.api.ConflictoVersionException si otra solicitud pendiente se creó en paralelo
     */
    long crear(long productoId, TipoRevision tipo, DatosProducto propuesto, int versionBase, long solicitadoPor,
               Instant ahora);

    Optional<Revision> pendienteDe(long productoId);

    /** Lee la revisión bloqueándola ({@code FOR UPDATE}) hasta el fin de la transacción. */
    Optional<Revision> bloquear(long id);

    /** Una nueva edición del vendedor reemplaza la propuesta que seguía pendiente. */
    void reemplazarPropuesta(long id, DatosProducto propuesto, int versionBase, long solicitadoPor, Instant ahora);

    void resolver(long id, EstadoRevision estado, String motivoRechazo, Long revisadoPor, Instant ahora);

    /** Aprobada con cambios: guarda lo que el ADMIN aprobó (la revisión ya debe estar APROBADA). */
    void guardarAprobados(long id, DatosProducto aprobados);

    /** @param estado {@code null} = todas */
    Pagina<Revision> buscar(EstadoRevision estado, int page, int size);

    List<Revision> deProducto(long productoId);

    record Revision(
            long id,
            long productoId,
            TipoRevision tipo,
            EstadoRevision estado,
            DatosProducto propuesto,
            DatosProducto aprobado,
            int versionBase,
            String motivoRechazo,
            long solicitadoPor,
            Instant solicitadoEn,
            Long revisadoPor,
            Instant revisadoEn) {
    }
}
