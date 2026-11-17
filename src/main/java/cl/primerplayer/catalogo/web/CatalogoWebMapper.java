package cl.primerplayer.catalogo.web;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import cl.primerplayer.catalogo.api.ConsultarCatalogoPublicoUseCase.ResultadoCatalogo;
import cl.primerplayer.catalogo.api.DatosProducto;
import cl.primerplayer.catalogo.api.ProductoPublico;
import cl.primerplayer.catalogo.web.dto.CatalogoPaginaResponse;
import cl.primerplayer.catalogo.web.dto.ProductoPublicoResponse;
import cl.primerplayer.catalogo.api.DatosProducto.ImagenProducto;
import cl.primerplayer.catalogo.api.ProductoAdmin;
import cl.primerplayer.catalogo.web.dto.ImagenDto;
import cl.primerplayer.catalogo.web.dto.ProductoRequest;
import cl.primerplayer.catalogo.web.dto.ProductoResponse;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface CatalogoWebMapper {

    @Mapping(target = "limitePorCliente", source = "limitePorUsuario")
    @Mapping(target = "publicado", expression = "java(Boolean.TRUE.equals(request.publicado()))")
    DatosProducto toDatos(ProductoRequest request);

    @Mapping(target = "limitePorUsuario", source = "limitePorCliente")
    @Mapping(target = "fecha", source = "fechaIngreso")
    @Mapping(target = "imagen", expression = "java(portada(producto.imagenes()))")
    ProductoResponse toResponse(ProductoAdmin producto);

    ImagenProducto toImagen(ImagenDto dto);

    ImagenDto toDto(ImagenProducto imagen);

    @Mapping(target = "limitePorUsuario", source = "limitePorCliente")
    @Mapping(target = "fecha", source = "fechaIngreso")
    @Mapping(target = "imagen", expression = "java(portada(producto.imagenes()))")
    @Mapping(target = "imagenes", expression = "java(urlsPortadaPrimero(producto.imagenes()))")
    ProductoPublicoResponse toPublico(ProductoPublico producto);

    default CatalogoPaginaResponse toPagina(ResultadoCatalogo resultado) {
        var p = resultado.pagina();
        return new CatalogoPaginaResponse(p.content().stream().map(this::toPublico).toList(),
                p.page(), p.size(), p.totalElements(), p.totalPages(), resultado.facetas());
    }

    default String portada(List<ImagenProducto> imagenes) {
        return imagenes.stream().filter(ImagenProducto::portada).map(ImagenProducto::url).findFirst().orElse(null);
    }

    /** Portada primero y luego el resto en su posición (el frontend usa {@code imagenes[0]} como principal). */
    default List<String> urlsPortadaPrimero(List<ImagenProducto> imagenes) {
        return imagenes.stream()
                .sorted(java.util.Comparator.comparing(ImagenProducto::portada).reversed())
                .map(ImagenProducto::url)
                .toList();
    }
}
