package cl.primerplayer.media.web;

import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import cl.primerplayer.media.api.ArchivosUseCase;
import cl.primerplayer.media.api.ArchivosUseCase.ArchivoSubido;
import cl.primerplayer.shared.api.FechasTienda;
import cl.primerplayer.shared.api.UsuarioActual;
import cl.primerplayer.shared.api.ValidacionException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Subida de archivos (VENDEDOR o ADMIN). Multipart con el campo {@code archivo}; máx. 5 MB; JPEG, PNG o WebP. */
@RestController
@RequestMapping("/api/v1/admin/media")
@PreAuthorize("hasAnyRole('VENDEDOR','ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin · Archivos")
@SecurityRequirement(name = "bearer")
class MediaController {

    private final ArchivosUseCase archivos;

    @PostMapping(value = "/imagenes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Subir una imagen pública (productos y eventos)",
            description = "Devuelve {id, url}. Usa el id como archivoId en las imágenes del producto o del evento.")
    ResponseEntity<ArchivoResponse> imagen(@RequestParam("archivo") MultipartFile archivo,
                                           @AuthenticationPrincipal UsuarioActual usuario) {
        ArchivoSubido s = archivos.subirImagen(bytes(archivo), usuario.id());
        return ResponseEntity.created(URI.create(s.url())).body(ArchivoResponse.de(s));
    }

    @PostMapping(value = "/comprobantes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Subir un comprobante de transferencia (privado)",
            description = "Devuelve {id}. Úsalo como transferProofId en el pago por transferencia del POS.")
    ResponseEntity<ArchivoResponse> comprobante(@RequestParam("archivo") MultipartFile archivo,
                                                @AuthenticationPrincipal UsuarioActual usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ArchivoResponse.de(archivos.subirComprobante(bytes(archivo),
                usuario.id())));
    }

    @GetMapping("/comprobantes/{id}")
    @Operation(summary = "URL firmada para ver un comprobante (válida 5 minutos)")
    UrlResponse verComprobante(@PathVariable UUID id) {
        var u = archivos.urlComprobante(id);
        return new UrlResponse(u.url(), FechasTienda.enZona(u.expiraEn()));
    }

    private static byte[] bytes(MultipartFile archivo) {
        try {
            return archivo.getBytes();
        } catch (IOException e) {
            throw new ValidacionException("archivo", "No se pudo leer el archivo.");
        }
    }

    record ArchivoResponse(UUID id, String url, String mime, int bytes, Integer width, Integer height) {
        static ArchivoResponse de(ArchivoSubido s) {
            return new ArchivoResponse(s.id(), s.url(), s.mime(), s.bytes(), s.ancho(), s.alto());
        }
    }

    record UrlResponse(String url, OffsetDateTime expiresAt) {}
}
