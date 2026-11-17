package cl.primerplayer.catalogo.application.port.out;

import java.util.List;
import java.util.Optional;

import cl.primerplayer.catalogo.api.ConsultarCatalogoPublicoUseCase.FiltroCatalogo;
import cl.primerplayer.catalogo.api.ConsultarCatalogoPublicoUseCase.ResultadoCatalogo;
import cl.primerplayer.catalogo.api.ProductoPublico;

/** Puerto de salida: lecturas del catálogo público (solo publicados y no eliminados). */
public interface CatalogoPublicoRepositorio {

    ResultadoCatalogo buscar(FiltroCatalogo filtro);

    List<ProductoPublico> masPopulares(int limite);

    Optional<ProductoPublico> visible(long id);

    List<ProductoPublico> visibles(java.util.Collection<Long> ids);

    List<ProductoPublico> relacionados(long id, int limite);
}
