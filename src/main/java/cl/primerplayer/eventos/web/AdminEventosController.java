package cl.primerplayer.eventos.web;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.primerplayer.eventos.api.AdministrarEventosUseCase;
import cl.primerplayer.eventos.api.ConsultarEventosUseCase;
import cl.primerplayer.eventos.api.EventoVista;
import cl.primerplayer.eventos.web.dto.EventoDtos.EventoRequest;
import cl.primerplayer.eventos.web.dto.EventoDtos.EventoResponse;
import cl.primerplayer.eventos.web.dto.EventoDtos.PublicacionRequest;
import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.UsuarioActual;
import cl.primerplayer.shared.api.ValidacionException;
import cl.primerplayer.shared.api.VersionRequeridaException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/eventos")
@PreAuthorize("hasAnyRole('VENDEDOR','ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin · Eventos")
@SecurityRequirement(name = "bearer")
class AdminEventosController {

    private final AdministrarEventosUseCase administrar;
    private final ConsultarEventosUseCase consultar;
    private final EventosWebMapper mapper;

    @GetMapping
    @Operation(summary = "Listar eventos (incluye no publicados y pasados), por fecha")
    Pagina<EventoResponse> buscar(@RequestParam(required = false) List<String> tipo,
                                  @RequestParam(required = false) String desde,
                                  @RequestParam(required = false) String hasta,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "24") int size) {
        return consultar.buscarAdmin(Filtros.de(tipo, desde, hasta, page, size)).map(mapper::toAdmin);
    }

    @GetMapping("/{id}")
    ResponseEntity<EventoResponse> obtener(@PathVariable long id) {
        return conEtag(ResponseEntity.ok(), consultar.obtenerAdmin(id));
    }

    @PostMapping
    @Operation(summary = "Crear evento", description = "Errores: validation (400), slot-taken (409) si choca con un cumpleaños.")
    ResponseEntity<EventoResponse> crear(@Valid @RequestBody EventoRequest req) {
        EventoVista e = administrar.crear(mapper.toDatos(req));
        return conEtag(ResponseEntity.created(URI.create("/api/v1/admin/eventos/" + e.id())), e);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Reemplazar evento (If-Match)", description = "El límite por usuario ya configurado solo lo cambia ADMIN.")
    ResponseEntity<EventoResponse> actualizar(@PathVariable long id,
                                              @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                              @Valid @RequestBody EventoRequest req,
                                              @AuthenticationPrincipal UsuarioActual usuario) {
        return conEtag(ResponseEntity.ok(), administrar.actualizar(id, version(ifMatch), mapper.toDatos(req), usuario.rol()));
    }

    @PatchMapping("/{id}/publicacion")
    ResponseEntity<EventoResponse> publicacion(@PathVariable long id, @Valid @RequestBody PublicacionRequest req) {
        return conEtag(ResponseEntity.ok(), administrar.cambiarPublicacion(id, req.publicado()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar (borrado lógico); libera el bloque si era cumpleaños")
    ResponseEntity<Void> eliminar(@PathVariable long id) {
        administrar.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<EventoResponse> conEtag(ResponseEntity.BodyBuilder b, EventoVista e) {
        return b.eTag("\"" + e.version() + "\"").body(mapper.toAdmin(e));
    }

    private static int version(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new VersionRequeridaException();
        }
        try {
            return Integer.parseInt(ifMatch.strip().replaceFirst("^W/", "").replace("\"", ""));
        } catch (NumberFormatException e) {
            throw new ValidacionException("If-Match", "Debe ser la versión numérica del evento.");
        }
    }
}
