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
import cl.primerplayer.catalogo.api.RevisionesProductoUseCase.RevisionProducto;
import cl.primerplayer.catalogo.web.dto.RevisionDtos.PropuestaResponse;
import cl.primerplayer.catalogo.web.dto.RevisionDtos.RevisionResponse;

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

    default RevisionResponse toRevision(RevisionProducto r) {
        DatosProducto p = r.propuesto();
        List<String> campos = p == null || r.actual() == null ? List.of() : camposModificados(r.actual(), p);
        return new RevisionResponse(r.id(), r.productoId(), r.tipo(), r.estado(),
                r.actual() == null ? null : toResponse(r.actual()), toPropuesta(p), toPropuesta(r.aprobado()), campos,
                r.desactualizada(),
                r.motivoRechazo(), r.solicitadoPor(), r.solicitadoEn(), r.revisadoPor(), r.revisadoEn());
    }

    default PropuestaResponse toPropuesta(DatosProducto p) {
        return p == null ? null : new PropuestaResponse(p.nombre(), p.descripcion(), p.precio(), p.categoria(), p.tipo(),
                p.idioma(), p.marca(), p.plataforma(), p.franquicia(), p.linea(), p.codigoBarras(),
                p.limitePorCliente(), p.publicado(), portada(p.imagenes()), p.imagenes().stream().map(this::toDto).toList());
    }

    /** Nombres de campo como los ve el frontend ({@code limitePorUsuario}, no {@code limitePorCliente}). */
    default List<String> camposModificados(ProductoAdmin a, DatosProducto p) {
        List<String> c = new java.util.ArrayList<>();
        if (!java.util.Objects.equals(a.nombre(), p.nombre())) c.add("nombre");
        if (!java.util.Objects.equals(a.descripcion(), p.descripcion())) c.add("descripcion");
        if (a.precio() != p.precio()) c.add("precio");
        if (!java.util.Objects.equals(a.categoria(), p.categoria())) c.add("categoria");
        if (!java.util.Objects.equals(a.tipo(), p.tipo())) c.add("tipo");
        if (!java.util.Objects.equals(a.idioma(), p.idioma())) c.add("idioma");
        if (!java.util.Objects.equals(a.marca(), p.marca())) c.add("marca");
        if (!java.util.Objects.equals(a.plataforma(), p.plataforma())) c.add("plataforma");
        if (!java.util.Objects.equals(a.franquicia(), p.franquicia())) c.add("franquicia");
        if (!java.util.Objects.equals(a.linea(), p.linea())) c.add("linea");
        if (!java.util.Objects.equals(a.codigoBarras(), p.codigoBarras())) c.add("codigoBarras");
        if (!java.util.Objects.equals(a.limitePorCliente(), p.limitePorCliente())) c.add("limitePorUsuario");
        if (a.publicado() != p.publicado()) c.add("publicado");
        if (!clavesImagenes(a.imagenes()).equals(clavesImagenes(p.imagenes()))) c.add("imagenes");
        return c;
    }

    default List<String> clavesImagenes(List<ImagenProducto> imagenes) {
        return imagenes.stream()
                .map(i -> (i.archivoId() != null ? "archivo:" + i.archivoId() : "url:" + i.url()) + ":" + i.portada())
                .toList();
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
