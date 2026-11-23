package cl.primerplayer.catalogo.web;

import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.primerplayer.catalogo.api.AdministrarProductosUseCase;
import cl.primerplayer.catalogo.api.ConsultarProductosAdminUseCase;
import cl.primerplayer.catalogo.api.ConsultarProductosAdminUseCase.EstadoProducto;
import cl.primerplayer.catalogo.api.ConsultarProductosAdminUseCase.FiltroProductos;
import cl.primerplayer.catalogo.api.ConsultarProductosAdminUseCase.IndicadoresProductos;
import cl.primerplayer.catalogo.api.ConsultarProductosAdminUseCase.Orden;
import cl.primerplayer.catalogo.api.ProductoAdmin;
import cl.primerplayer.catalogo.infrastructure.excel.ProductosExcelService;
import cl.primerplayer.shared.api.VersionRequeridaException;
import cl.primerplayer.catalogo.web.dto.ProductoRequest;
import cl.primerplayer.catalogo.web.dto.ProductoResponse;
import cl.primerplayer.catalogo.web.dto.PublicacionRequest;
import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.UsuarioActual;
import cl.primerplayer.shared.api.ValidacionException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Mantenedor de productos. La versión viaja en {@code ETag} (respuesta) e {@code If-Match} (PUT). */
@RestController
@RequestMapping("/api/v1/admin/productos")
@PreAuthorize("hasAnyRole('VENDEDOR','ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin · Productos")
@SecurityRequirement(name = "bearer")
class AdminProductosController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdministrarProductosUseCase administrar;
    private final ConsultarProductosAdminUseCase consultar;
    private final CatalogoWebMapper mapper;
    private final ProductosExcelService productosExcel;

    @GetMapping
    @Operation(summary = "Buscar productos (incluye ocultos)")
    Pagina<ProductoResponse> buscar(
            @Parameter(description = "nombre, id o código de barras") @RequestParam(required = false) String q,
            @Parameter(description = "slug hoja o agrupadora; repetible") @RequestParam(required = false) List<String> categoria,
            @Parameter(description = "publicado | oculto | stock-bajo | agotado; repetible (OR)")
            @RequestParam(required = false) List<String> estado,
            @Parameter(description = "id | nombre | precio | stock | fecha, más ,asc o ,desc", example = "nombre,asc")
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ValidacionException("size", "page >= 0 y size entre 1 y " + MAX_PAGE_SIZE + ".");
        }
        var filtro = new FiltroProductos(q, categoria, estados(estado), orden(sort), page, size);
        return consultar.buscar(filtro).map(mapper::toResponse);
    }

    // Sin `produces`: el formato lo decide `format` o el Accept dentro del método. Con `produces`, un Accept
    // distinto (p. ej. application/octet-stream desde el frontend) hacía caer la petición en GET /{id}.
    @GetMapping("/exportar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Exportar todos los productos activos a JSON o Excel")
    ResponseEntity<?> exportar(@RequestParam(defaultValue = "json") String format,
                               @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        boolean excel = "excel".equalsIgnoreCase(format) || aceptaExcel(accept);
        List<ProductoAdmin> productos = consultar.todosActivos();
        if (!excel) {
            return ResponseEntity.ok(productos.stream().map(mapper::toResponse).toList());
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"stock-productos.xlsx\"")
                .body(productosExcel.exportarStock(productos));
    }

    @GetMapping("/indicadores")
    @Operation(summary = "Totales para el panel: {total, stockBajo, agotados}")
    IndicadoresProductos indicadores() {
        return consultar.indicadores();
    }

    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Obtener un producto (ETag = versión)")
    ResponseEntity<ProductoResponse> obtener(@PathVariable long id) {
        return conEtag(ResponseEntity.ok(), consultar.obtener(id));
    }

    @PostMapping
    @Operation(summary = "Crear producto (stock inicial 0)")
    ResponseEntity<ProductoResponse> crear(@Valid @RequestBody ProductoRequest request) {
        ProductoAdmin creado = administrar.crear(mapper.toDatos(request));
        return conEtag(ResponseEntity.created(URI.create("/api/v1/admin/productos/" + creado.id())), creado);
    }

    @PutMapping("/{id:\\d+}")
    @Operation(summary = "Reemplazar producto", description = "Exige If-Match con la versión leída. "
            + "Solo ADMIN agrega o cambia el código de barras y el límite ya configurado.")
    ResponseEntity<ProductoResponse> actualizar(@PathVariable long id,
                                                @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                @Valid @RequestBody ProductoRequest request,
                                                @AuthenticationPrincipal UsuarioActual usuario) {
        ProductoAdmin p = administrar.actualizar(id, version(ifMatch), mapper.toDatos(request), usuario.rol());
        return conEtag(ResponseEntity.ok(), p);
    }

    @PatchMapping("/{id:\\d+}/publicacion")
    @Operation(summary = "Publicar u ocultar")
    ResponseEntity<ProductoResponse> publicacion(@PathVariable long id, @Valid @RequestBody PublicacionRequest request) {
        return conEtag(ResponseEntity.ok(), administrar.cambiarPublicacion(id, request.publicado()));
    }

    @DeleteMapping("/{id:\\d+}")
    @Operation(summary = "Eliminar (borrado lógico)")
    ResponseEntity<Void> eliminar(@PathVariable long id) {
        administrar.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- helpers ----------

    private ResponseEntity<ProductoResponse> conEtag(ResponseEntity.BodyBuilder builder, ProductoAdmin p) {
        return builder.eTag("\"" + p.version() + "\"").body(mapper.toResponse(p));
    }

    private static boolean aceptaExcel(String accept) {
        if (accept == null) {
            return false;
        }
        return MediaType.parseMediaTypes(accept).stream()
                .anyMatch(tipo -> !tipo.isWildcardType() && !tipo.isWildcardSubtype()
                        && tipo.isCompatibleWith(MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
    }

    /** Acepta {@code "3"}, {@code 3} o {@code W/"3"}. */
    private static int version(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new VersionRequeridaException();
        }
        String v = ifMatch.strip().replaceFirst("^W/", "").replace("\"", "");
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new ValidacionException("If-Match", "Debe ser la versión numérica del producto.");
        }
    }

    private static Set<EstadoProducto> estados(List<String> valores) {
        if (valores == null) {
            return Set.of();
        }
        Set<EstadoProducto> r = EnumSet.noneOf(EstadoProducto.class);
        for (String v : valores) {
            r.add(switch (v.strip().toLowerCase(Locale.ROOT)) {
                case "publicado" -> EstadoProducto.PUBLICADO;
                case "oculto" -> EstadoProducto.OCULTO;
                case "stock-bajo" -> EstadoProducto.STOCK_BAJO;
                case "agotado" -> EstadoProducto.AGOTADO;
                default -> throw new ValidacionException("estado", "Valor no permitido: " + v);
            });
        }
        return r;
    }

    private static Orden orden(String sort) {
        if (sort == null || sort.isBlank()) {
            return null;
        }
        String[] partes = sort.split(",");
        Orden.Campo campo;
        try {
            campo = Orden.Campo.valueOf(partes[0].strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidacionException("sort", "Campo no permitido: " + partes[0]);
        }
        boolean asc = partes.length < 2 || !partes[1].strip().equalsIgnoreCase("desc");
        return new Orden(campo, asc);
    }
}
