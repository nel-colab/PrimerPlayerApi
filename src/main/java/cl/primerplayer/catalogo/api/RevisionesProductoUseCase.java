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
     * Aprobar con cambios: el ADMIN corrige los datos antes de aprobar y el producto queda exactamente con
     * {@code cambios.datos()} (incluido {@code publicado}). Se aplican las reglas de un ADMIN (puede cambiar código de
     * barras y límite). Una EDICION desactualizada sí se puede aprobar así: el ADMIN la ajustó sobre la versión actual.
     *
     * @param cambios {@code null} = aprobar tal cual ({@link #aprobar(long, long)})
     * @throws RevisionNoPendienteException                        si ya no está pendiente
     * @throws cl.primerplayer.shared.api.ConflictoVersionException si el producto cambió desde que el ADMIN lo leyó
     */
    RevisionProducto aprobar(long revisionId, CambiosAprobacion cambios, long adminId);

    /**
     * @param datos           lo que debe quedar en el producto
     * @param versionProducto versión del producto que vio el ADMIN al corregir (la de {@code actual.version})
     */
    record CambiosAprobacion(DatosProducto datos, int versionProducto) {}

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
     * @param aprobado       si un ADMIN la aprobó con cambios: los datos que aprobó; {@code null} si se aprobó tal cual
     * @param desactualizada la EDICION se propuso sobre una versión anterior del producto: no se puede aprobar
     */
    record RevisionProducto(
            long id,
            long productoId,
            TipoRevision tipo,
            EstadoRevision estado,
            ProductoAdmin actual,
            DatosProducto propuesto,
            DatosProducto aprobado,
            boolean desactualizada,
            String motivoRechazo,
            long solicitadoPor,
            Instant solicitadoEn,
            Long revisadoPor,
            Instant revisadoEn) {
    }
}
