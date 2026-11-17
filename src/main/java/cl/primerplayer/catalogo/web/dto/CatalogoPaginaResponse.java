package cl.primerplayer.catalogo.web.dto;

import java.util.List;

import cl.primerplayer.catalogo.api.ConsultarCatalogoPublicoUseCase.Facetas;

/** Formato común de página + {@code facets} para los filtros de la tienda. */
public record CatalogoPaginaResponse(
        List<ProductoPublicoResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        Facetas facets) {
}
