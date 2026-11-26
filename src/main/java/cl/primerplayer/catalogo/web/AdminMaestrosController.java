package cl.primerplayer.catalogo.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import cl.primerplayer.catalogo.api.AdministrarMaestrosUseCase;
import cl.primerplayer.catalogo.api.AdministrarMaestrosUseCase.CategoriaAdmin;
import cl.primerplayer.catalogo.api.AdministrarMaestrosUseCase.FranquiciaAdmin;
import cl.primerplayer.shared.api.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** Mantenedor de categorías y franquicias (solo ADMIN), en la configuración de inventario del panel. */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin · Categorías y franquicias")
@SecurityRequirement(name = "bearer")
class AdminMaestrosController {

    private final AdministrarMaestrosUseCase maestros;

    // ---------- categorías ----------

    @GetMapping("/categorias")
    @Operation(summary = "Categorías asignables con su uso", description = "Sin la agrupadora 'todos'. "
            + "enUso = alguna vez se asoció a un producto (no se puede eliminar).")
    List<CategoriaResponse> categorias() {
        return maestros.categorias().stream().map(CategoriaResponse::de).toList();
    }

    @PostMapping("/categorias")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear categoría", description = "slug opcional (se genera del título) e inmutable. "
            + "Errores: category-exists (409), validation (400).")
    CategoriaResponse crearCategoria(@Valid @RequestBody CrearCategoriaRequest req,
                                     @AuthenticationPrincipal UsuarioActual usuario) {
        return CategoriaResponse.de(maestros.crearCategoria(usuario.id(), req.titulo(), req.slug(), req.orden()));
    }

    @PutMapping("/categorias/{id}")
    @Operation(summary = "Cambiar título u orden de una categoría", description = "Errores: category-exists (409), "
            + "item-unavailable (404).")
    CategoriaResponse actualizarCategoria(@PathVariable int id, @Valid @RequestBody ActualizarCategoriaRequest req,
                                          @AuthenticationPrincipal UsuarioActual usuario) {
        return CategoriaResponse.de(maestros.actualizarCategoria(usuario.id(), id, req.titulo(), req.orden()));
    }

    @DeleteMapping("/categorias/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar categoría", description = "Solo si nunca se asoció a un producto. "
            + "Errores: category-in-use (409), item-unavailable (404).")
    void eliminarCategoria(@PathVariable int id, @AuthenticationPrincipal UsuarioActual usuario) {
        maestros.eliminarCategoria(usuario.id(), id);
    }

    // ---------- franquicias ----------

    @GetMapping("/franquicias")
    @Operation(summary = "Franquicias con su uso")
    List<FranquiciaResponse> franquicias() {
        return maestros.franquicias().stream().map(FranquiciaResponse::de).toList();
    }

    @PostMapping("/franquicias")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear franquicia", description = "Errores: franchise-exists (409), validation (400).")
    FranquiciaResponse crearFranquicia(@Valid @RequestBody FranquiciaRequest req,
                                       @AuthenticationPrincipal UsuarioActual usuario) {
        return FranquiciaResponse.de(maestros.crearFranquicia(usuario.id(), req.nombre()));
    }

    @PutMapping("/franquicias/{id}")
    @Operation(summary = "Renombrar franquicia", description = "Los productos la siguen teniendo. "
            + "Errores: franchise-exists (409), item-unavailable (404).")
    FranquiciaResponse renombrarFranquicia(@PathVariable int id, @Valid @RequestBody FranquiciaRequest req,
                                           @AuthenticationPrincipal UsuarioActual usuario) {
        return FranquiciaResponse.de(maestros.renombrarFranquicia(usuario.id(), id, req.nombre()));
    }

    @DeleteMapping("/franquicias/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar franquicia", description = "Solo si nunca se asoció a un producto. "
            + "Errores: franchise-in-use (409), item-unavailable (404).")
    void eliminarFranquicia(@PathVariable int id, @AuthenticationPrincipal UsuarioActual usuario) {
        maestros.eliminarFranquicia(usuario.id(), id);
    }

    // ---------- DTO ----------

    record CrearCategoriaRequest(
            @Schema(example = "Juegos de mesa") @NotBlank @Size(max = 80) String titulo,
            @Schema(example = "juegos-de-mesa", description = "opcional: se genera del título") @Size(max = 60) String slug,
            @Schema(example = "60", description = "opcional: al final") @Min(0) @Max(9999) Integer orden) {}

    record ActualizarCategoriaRequest(
            @NotBlank @Size(max = 80) String titulo,
            @Schema(description = "opcional: se mantiene") @Min(0) @Max(9999) Integer orden) {}

    record FranquiciaRequest(@Schema(example = "Digimon") @NotBlank @Size(max = 60) String nombre) {}

    record CategoriaResponse(int id, String slug, String titulo, int orden,
                             @Schema(description = "productos que la usan (incluidos eliminados)") long productos,
                             @Schema(description = "alguna vez se asoció a un producto: no se puede eliminar") boolean enUso) {
        static CategoriaResponse de(CategoriaAdmin c) {
            return new CategoriaResponse(c.id(), c.slug(), c.titulo(), c.orden(), c.productos(), c.enUso());
        }
    }

    record FranquiciaResponse(int id, String nombre, long productos, boolean enUso) {
        static FranquiciaResponse de(FranquiciaAdmin f) {
            return new FranquiciaResponse(f.id(), f.nombre(), f.productos(), f.enUso());
        }
    }
}
