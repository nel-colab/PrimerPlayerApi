package cl.primerplayer.clientes.web;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.primerplayer.catalogo.api.ProductoPublico;
import cl.primerplayer.clientes.api.CuentaClienteUseCase;
import cl.primerplayer.clientes.api.CuentaClienteUseCase.DatosDireccion;
import cl.primerplayer.clientes.api.CuentaClienteUseCase.Direccion;
import cl.primerplayer.shared.api.FechasTienda;
import cl.primerplayer.shared.api.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** Direcciones, favoritos y alertas de stock (cualquier usuario con sesión, incluido el personal; el id sale siempre del token). */
@RestController
@RequestMapping("/api/v1/me")
@PreAuthorize("hasAnyRole('CLIENTE','VENDEDOR','ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Cliente · Cuenta")
@SecurityRequirement(name = "bearer")
class CuentaClienteController {

    private final CuentaClienteUseCase cuenta;

    // ---------- direcciones ----------

    @GetMapping("/direcciones")
    @Operation(summary = "Mis direcciones (la principal primero)")
    List<Direccion> direcciones(@AuthenticationPrincipal UsuarioActual u) {
        return cuenta.direcciones(u.id());
    }

    @PostMapping("/direcciones")
    @Operation(summary = "Agregar una dirección (máx. 5)", description = "Por ahora solo hay retiro en tienda: se guardan para el despacho futuro.")
    ResponseEntity<Direccion> crear(@Valid @RequestBody DireccionRequest req, @AuthenticationPrincipal UsuarioActual u) {
        Direccion d = cuenta.crearDireccion(u.id(), req.datos());
        return ResponseEntity.created(URI.create("/api/v1/me/direcciones/" + d.id())).body(d);
    }

    @PutMapping("/direcciones/{id}")
    @Operation(summary = "Editar una dirección", description = "404 si no existe o es de otro cliente.")
    Direccion actualizar(@PathVariable long id, @Valid @RequestBody DireccionRequest req, @AuthenticationPrincipal UsuarioActual u) {
        return cuenta.actualizarDireccion(u.id(), id, req.datos());
    }

    @DeleteMapping("/direcciones/{id}")
    @Operation(summary = "Eliminar una dirección")
    ResponseEntity<Void> eliminar(@PathVariable long id, @AuthenticationPrincipal UsuarioActual u) {
        cuenta.eliminarDireccion(u.id(), id);
        return ResponseEntity.noContent().build();
    }

    // ---------- favoritos ----------

    @GetMapping("/favoritos")
    @Operation(summary = "Mis favoritos (solo productos a la venta)")
    List<MarcaResponse> favoritos(@AuthenticationPrincipal UsuarioActual u) {
        return cuenta.favoritos(u.id()).stream().map(f -> MarcaResponse.de(f.producto(), f.agregadoEn(), null)).toList();
    }

    @PutMapping("/favoritos/{productoId}")
    @Operation(summary = "Agregar a favoritos (idempotente)", description = "204. 404 si el producto no está a la venta.")
    ResponseEntity<Void> agregarFavorito(@PathVariable long productoId, @AuthenticationPrincipal UsuarioActual u) {
        cuenta.agregarFavorito(u.id(), productoId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/favoritos/{productoId}")
    @Operation(summary = "Quitar de favoritos")
    ResponseEntity<Void> quitarFavorito(@PathVariable long productoId, @AuthenticationPrincipal UsuarioActual u) {
        cuenta.quitarFavorito(u.id(), productoId);
        return ResponseEntity.noContent().build();
    }

    // ---------- alertas de stock ----------

    @GetMapping("/alertas-stock")
    @Operation(summary = "Mis alertas de stock", description = "notifiedAt = null: pendiente (avisará por correo cuando vuelva el stock).")
    List<MarcaResponse> alertas(@AuthenticationPrincipal UsuarioActual u) {
        return cuenta.alertas(u.id()).stream().map(a -> MarcaResponse.de(a.producto(), a.creadaEn(), a.notificadaEn())).toList();
    }

    @PutMapping("/alertas-stock/{productoId}")
    @Operation(summary = "Avisarme cuando vuelva el stock (idempotente)")
    ResponseEntity<Void> crearAlerta(@PathVariable long productoId, @AuthenticationPrincipal UsuarioActual u) {
        cuenta.crearAlerta(u.id(), productoId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/alertas-stock/{productoId}")
    @Operation(summary = "Quitar una alerta de stock")
    ResponseEntity<Void> quitarAlerta(@PathVariable long productoId, @AuthenticationPrincipal UsuarioActual u) {
        cuenta.quitarAlerta(u.id(), productoId);
        return ResponseEntity.noContent().build();
    }

    // ---------- DTO ----------

    record DireccionRequest(@Schema(example = "Casa") @NotBlank @Size(max = 40) String alias,
                            @Schema(example = "Freire 456") @NotBlank @Size(max = 160) String calle,
                            @Schema(example = "San Bernardo") @NotBlank @Size(max = 80) String comuna,
                            @Schema(example = "Región Metropolitana") @NotBlank @Size(max = 80) String region,
                            @Size(max = 200) String referencia,
                            boolean principal) {
        DatosDireccion datos() {
            return new DatosDireccion(alias, calle, comuna, region, referencia, principal);
        }
    }

    /** Producto (forma corta) + fechas. {@code notifiedAt} solo en alertas. */
    record MarcaResponse(long productId, String name, long price, String image, int stock, OffsetDateTime createdAt,
                         OffsetDateTime notifiedAt) {
        static MarcaResponse de(ProductoPublico p, java.time.Instant creada, java.time.Instant notificada) {
            String portada = p.imagenes().stream().filter(i -> i.portada()).map(i -> i.url()).findFirst().orElse(null);
            return new MarcaResponse(p.id(), p.nombre(), p.precio(), portada, p.stock(), FechasTienda.enZona(creada),
                    FechasTienda.enZona(notificada));
        }
    }
}
