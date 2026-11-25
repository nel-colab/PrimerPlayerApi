package cl.primerplayer.catalogo.api;

import java.time.Instant;
import java.util.List;

import cl.primerplayer.shared.api.Pagina;

/**
 * Puerto de entrada: autorización de productos. Lo que propone un VENDEDOR (alta o edición) queda como revisión
 * pendiente; solo un ADMIN la aprueba o la rechaza.
 */
public interface RevisionesProductoUseCase {

    /** Bandeja: {@code estado} {@code null} = todas. Las más antiguas primero. */
    Pagina<RevisionProducto> buscar(EstadoRevision estado, int page, int size);

    /** Historial de un producto, la más reciente primero. */
    List<RevisionProducto> deProducto(long productoId);

    /**
     * Alta: aprueba y publica el producto. Edición: copia los datos propuestos al producto.
     *
     * @throws RevisionNoPendienteException     si ya no está pendiente
     * @throws RevisionDesactualizadaException  si el producto cambió desde que se propuso la edición
     */
    RevisionProducto aprobar(long revisionId, long adminId);

    /**
     * Alta: el producto queda {@link EstadoAprobacion#RECHAZADO} (el vendedor lo corrige y vuelve a revisión).
     * Edición: la propuesta se descarta; el producto no cambia.
     *
     * @throws RevisionNoPendienteException si ya no está pendiente
     */
    RevisionProducto rechazar(long revisionId, String motivo, long adminId);

    enum TipoRevision { ALTA, EDICION }

    enum EstadoRevision { PENDIENTE, APROBADA, RECHAZADA, DESCARTADA }

    /**
     * @param actual         el producto como está hoy
     * @param propuesto      datos propuestos (solo en una EDICION; en un ALTA lo propuesto es el propio producto)
     * @param desactualizada la EDICION se propuso sobre una versión anterior del producto: no se puede aprobar
     */
    record RevisionProducto(
            long id,
            long productoId,
            TipoRevision tipo,
            EstadoRevision estado,
            ProductoAdmin actual,
            DatosProducto propuesto,
            boolean desactualizada,
            String motivoRechazo,
            long solicitadoPor,
            Instant solicitadoEn,
            Long revisadoPor,
            Instant revisadoEn) {
    }
}
