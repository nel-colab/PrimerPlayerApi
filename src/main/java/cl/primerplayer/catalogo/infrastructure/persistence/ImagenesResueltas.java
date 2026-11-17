package cl.primerplayer.catalogo.infrastructure.persistence;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import cl.primerplayer.catalogo.api.DatosProducto.ImagenProducto;
import cl.primerplayer.media.api.ArchivosUseCase;
import lombok.RequiredArgsConstructor;

/** Convierte filas de {@code producto_imagen} en imágenes con URL: externa tal cual, o la del archivo subido (en lote). */
@Component
@RequiredArgsConstructor
class ImagenesResueltas {

    private final ArchivosUseCase media;

    List<ImagenProducto> de(List<ProductoImagenEntity> filas) {
        Map<UUID, String> urls = urlsDeArchivos(filas);
        return filas.stream()
                .sorted(Comparator.comparingInt(ProductoImagenEntity::getPosicion))
                .map(i -> new ImagenProducto(i.getArchivoId() == null ? i.getUrlExterna() : urls.get(i.getArchivoId()),
                        i.isEsPortada(), i.getArchivoId()))
                .toList();
    }

    /** Imágenes de muchos productos con una sola consulta a media. */
    Map<Long, List<ImagenProducto>> porProducto(List<ProductoImagenEntity> filas) {
        Map<UUID, String> urls = urlsDeArchivos(filas);
        return filas.stream()
                .sorted(Comparator.comparingInt(ProductoImagenEntity::getPosicion))
                .collect(Collectors.groupingBy(ProductoImagenEntity::getProductoId, Collectors.mapping(
                        i -> new ImagenProducto(i.getArchivoId() == null ? i.getUrlExterna() : urls.get(i.getArchivoId()),
                                i.isEsPortada(), i.getArchivoId()),
                        Collectors.toList())));
    }

    private Map<UUID, String> urlsDeArchivos(List<ProductoImagenEntity> filas) {
        List<UUID> ids = filas.stream().map(ProductoImagenEntity::getArchivoId).filter(Objects::nonNull).distinct().toList();
        return media.urlsPublicas(ids);
    }
}
