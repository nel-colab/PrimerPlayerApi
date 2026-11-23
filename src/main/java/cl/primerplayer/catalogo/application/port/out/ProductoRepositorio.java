package cl.primerplayer.catalogo.application.port.out;

import java.util.Optional;
import java.util.List;

import cl.primerplayer.catalogo.api.ConsultarProductosAdminUseCase.FiltroProductos;
import cl.primerplayer.catalogo.api.ConsultarProductosAdminUseCase.IndicadoresProductos;
import cl.primerplayer.catalogo.domain.Producto;
import cl.primerplayer.shared.api.Pagina;

/** Puerto de salida: persistencia de productos. Nunca escribe la columna {@code stock}. */
public interface ProductoRepositorio {

    /** Producto no eliminado. */
    Optional<Producto> buscarActivo(long id);

    /** Producto no eliminado con ese código de barras exacto (único por índice parcial). */
    Optional<Producto> buscarPorCodigoBarras(String codigo);

    Optional<Producto> buscarPorNombreExacto(String nombre);

    List<Producto> todosActivos();

    /** @throws cl.primerplayer.catalogo.api.CodigoBarrasEnUsoException */
    Producto guardarNuevo(Producto producto);

    /**
     * Guarda los cambios si la versión en la base sigue siendo {@code versionEsperada}.
     *
     * @throws cl.primerplayer.shared.api.ConflictoVersionException
     * @throws cl.primerplayer.catalogo.api.CodigoBarrasEnUsoException
     */
    Producto actualizar(Producto producto, int versionEsperada);

    Pagina<Producto> buscar(FiltroProductos filtro, int umbralStockBajo);

    IndicadoresProductos indicadores(int umbralStockBajo);
}
