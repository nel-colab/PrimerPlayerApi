package cl.primerplayer.catalogo.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import cl.primerplayer.shared.api.Pagina;

/** Puerto de entrada: lecturas del mantenedor (incluye productos no publicados; nunca eliminados). */
public interface ConsultarProductosAdminUseCase {

    /** @throws cl.primerplayer.shared.api.RecursoNoEncontradoException si no existe o está eliminado */
    ProductoAdmin obtener(long id);

    /** Búsqueda exacta para importación, incluye productos no publicados. */
    Optional<ProductoAdmin> porCodigoBarrasActivo(String codigo);

    /** Búsqueda exacta, sin distinguir mayúsculas, para importación. */
    Optional<ProductoAdmin> porNombreExacto(String nombre);

    /** Productos no eliminados, incluidos los no publicados. */
    List<ProductoAdmin> todosActivos();

    /**
     * Producto vendible por su código de barras (lo que escribe la pistola lectora en el POS, o el vendedor a mano):
     * coincidencia exacta, publicado y no eliminado.
     *
     * @throws cl.primerplayer.shared.api.RecursoNoEncontradoException 404 {@code item-unavailable} si no hay uno vendible
     */
    ProductoAdmin porCodigoBarras(String codigo);

    Pagina<ProductoAdmin> buscar(FiltroProductos filtro);

    IndicadoresProductos indicadores();

    /**
     * @param q          nombre (sin tildes ni mayúsculas), id o código de barras
     * @param categorias slugs (hoja o agrupadora: una agrupadora incluye sus hojas)
     * @param estados    se combinan con OR
     * @param orden      campo de la lista blanca + dirección
     */
    record FiltroProductos(String q, List<String> categorias, Set<EstadoProducto> estados, Orden orden,
                           int page, int size) {

        public FiltroProductos {
            categorias = categorias == null ? List.of() : List.copyOf(categorias);
            estados = estados == null ? Set.of() : Set.copyOf(estados);
        }
    }

    enum EstadoProducto { PUBLICADO, OCULTO, STOCK_BAJO, AGOTADO }

    record Orden(Campo campo, boolean ascendente) {
        public enum Campo { ID, NOMBRE, PRECIO, STOCK, FECHA }
    }

    record IndicadoresProductos(long total, long stockBajo, long agotados) {}
}
