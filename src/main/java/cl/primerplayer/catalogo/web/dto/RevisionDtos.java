package cl.primerplayer.catalogo.web.dto;

import java.time.Instant;
import java.util.List;

import cl.primerplayer.catalogo.api.RevisionesProductoUseCase.EstadoRevision;
import cl.primerplayer.catalogo.api.RevisionesProductoUseCase.TipoRevision;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Autorización de productos: lo que un VENDEDOR crea o cambia espera la aprobación de un ADMIN. */
public final class RevisionDtos {

    private RevisionDtos() {}

    public record RechazoRequest(
            @Schema(example = "La foto de portada no corresponde al producto.") @NotBlank @Size(max = 500) String motivo) {
    }

    /**
     * @param actual            el producto como está hoy (en un ALTA, lo que se va a aprobar)
     * @param propuesto         solo en una EDICION: los datos que quedarían al aprobar
     * @param camposModificados solo en una EDICION: campos de {@code propuesto} distintos de {@code actual}
     * @param desactualizada    la EDICION se propuso sobre otra versión del producto: no se puede aprobar
     *                          ({@code review-outdated}); se rechaza y se vuelve a proponer
     */
    public record RevisionResponse(
            long id,
            long productoId,
            @Schema(description = "ALTA (producto nuevo) | EDICION (cambio a un producto aprobado)") TipoRevision tipo,
            @Schema(description = "PENDIENTE | APROBADA | RECHAZADA | DESCARTADA") EstadoRevision estado,
            ProductoResponse actual,
            PropuestaResponse propuesto,
            List<String> camposModificados,
            boolean desactualizada,
            String motivoRechazo,
            long solicitadoPor,
            Instant solicitadoEn,
            Long revisadoPor,
            Instant revisadoEn) {
    }

    /** Misma forma que el cuerpo de {@code PUT /admin/productos/{id}} (con las URLs de las imágenes resueltas). */
    public record PropuestaResponse(
            String nombre,
            String descripcion,
            long precio,
            String categoria,
            String tipo,
            String idioma,
            String marca,
            String plataforma,
            String franquicia,
            String linea,
            String codigoBarras,
            Integer limitePorUsuario,
            boolean publicado,
            String imagen,
            List<ImagenDto> imagenes) {
    }
}
