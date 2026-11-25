package cl.primerplayer.catalogo.application;

import org.springframework.stereotype.Component;

import cl.primerplayer.catalogo.api.DatosProducto;
import cl.primerplayer.catalogo.application.port.out.MaestrosCatalogoRepositorio;
import cl.primerplayer.media.api.ArchivosUseCase;
import cl.primerplayer.shared.api.ValidacionException;
import lombok.RequiredArgsConstructor;

/** Reglas de un producto que necesitan la base: categoría hoja, franquicia existente e imágenes subidas públicas. */
@Component
@RequiredArgsConstructor
class ReferenciasProducto {

    private final MaestrosCatalogoRepositorio maestros;
    private final ArchivosUseCase media;

    void validar(DatosProducto datos) {
        Boolean hoja = maestros.categoriaEsHoja(datos.categoria().strip())
                .orElseThrow(() -> new ValidacionException("categoria", "La categoría no existe."));
        if (!hoja) {
            throw new ValidacionException("categoria", "La categoría debe ser una hoja, no una agrupadora.");
        }
        if (datos.franquicia() != null && !datos.franquicia().isBlank()
                && !maestros.existeFranquicia(datos.franquicia().strip())) {
            throw new ValidacionException("franquicia", "La franquicia no existe.");
        }
        // Una imagen subida debe existir y ser pública: un comprobante privado nunca puede quedar como foto.
        for (var img : datos.imagenes()) {
            if (img.archivoId() != null && !media.esImagenPublica(img.archivoId())) {
                throw new ValidacionException("imagenes", "La imagen subida no existe o no es una imagen pública.");
            }
        }
    }
}
