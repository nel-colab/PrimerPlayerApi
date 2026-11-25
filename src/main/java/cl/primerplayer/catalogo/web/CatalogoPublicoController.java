package cl.primerplayer.catalogo.web;

import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.primerplayer.catalogo.api.ConsultarCatalogoPublicoUseCase;
import cl.primerplayer.catalogo.api.ConsultarCatalogoPublicoUseCase.FiltroCatalogo;
import cl.primerplayer.catalogo.api.ConsultarCatalogoPublicoUseCase.OrdenCatalogo;
import cl.primerplayer.catalogo.api.ConsultarCategoriasUseCase;
import cl.primerplayer.catalogo.api.ConsultarCategoriasUseCase.NodoCategoria;
import cl.primerplayer.catalogo.web.dto.CatalogoPaginaResponse;
import cl.primerplayer.catalogo.web.dto.ProductoPublicoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Tienda pública (sin sesión): solo productos publicados y no eliminados. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Catálogo público")
class CatalogoPublicoController {

    private final ConsultarCategoriasUseCase categorias;
    private final ConsultarCatalogoPublicoUseCase catalogo;
    private final CatalogoWebMapper mapper;

    @GetMapping("/categorias")
    @Operation(summary = "Árbol de categorías {slug, titulo, hoja, hijos[]}")
    List<NodoCategoria> categorias() {
        return categorias.arbol();
    }

    @GetMapping("/franquicias")
    @Operation(summary = "Nombres de franquicias")
    List<String> franquicias() {
        return categorias.franquicias();
    }

    @GetMapping("/productos")
    @Operation(summary = "Buscar productos con filtros, orden y facetas",
            description = "Los filtros de lista (tipo, idioma, ...) son repetibles: OR dentro de la misma faceta, AND entre "
                    + "facetas. Cada faceta se calcula sobre el resultado sin su propio filtro.")
    CatalogoPaginaResponse buscar(
            @Parameter(description = "slug hoja o agrupadora; 'todos' = sin filtro", example = "tcg")
            @RequestParam(required = false) String categoria,
            @Parameter(description = "texto en el nombre (sin importar tildes)") @RequestParam(required = false) String q,
            @Parameter(description = "solo con stock") @RequestParam(defaultValue = "false") boolean enStock,
            @RequestParam(required = false) Long precioMin,
            @RequestParam(required = false) Long precioMax,
            @RequestParam(required = false) Set<String> tipo,
            @RequestParam(required = false) Set<String> idioma,
            @RequestParam(required = false) Set<String> marca,
            @RequestParam(required = false) Set<String> plataforma,
            @RequestParam(required = false) Set<String> franquicia,
            @RequestParam(required = false) Set<String> linea,
            @Parameter(description = "recomendados | recientes | precio-asc | precio-desc")
            @RequestParam(defaultValue = "recomendados") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        var filtro = new FiltroCatalogo(categoria, q, enStock, precioMin, precioMax, tipo, idioma, marca, plataforma,
                franquicia, linea, OrdenCatalogo.deParametro(sort), page, size);
        return mapper.toPagina(catalogo.buscar(filtro));
    }

    @GetMapping("/productos/destacados")
    @Operation(summary = "Productos más populares")
    List<ProductoPublicoResponse> destacados(
            @Parameter(description = "1 a " + ConsultarCatalogoPublicoUseCase.MAX_DESTACADOS)
            @RequestParam(defaultValue = "8") int limit) {
        return catalogo.destacados(limit).stream().map(mapper::toPublico).toList();
    }

    @GetMapping("/productos/{id}")
    @Operation(summary = "Detalle de un producto publicado (404 item-unavailable si está oculto o eliminado)")
    ProductoPublicoResponse obtener(@PathVariable long id) {
        return mapper.toPublico(catalogo.obtener(id));
    }

    @GetMapping("/productos/{id}/relacionados")
    @Operation(summary = "Productos relacionados: misma categoría y luego misma franquicia")
    List<ProductoPublicoResponse> relacionados(
            @PathVariable long id,
            @Parameter(description = "1 a " + ConsultarCatalogoPublicoUseCase.MAX_RELACIONADOS)
            @RequestParam(defaultValue = "4") int limit) {
        return catalogo.relacionados(id, limit).stream().map(mapper::toPublico).toList();
    }
}
