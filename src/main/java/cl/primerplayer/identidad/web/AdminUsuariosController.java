package cl.primerplayer.identidad.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.primerplayer.identidad.api.AdminUsuariosUseCase;
import cl.primerplayer.identidad.api.AdminUsuariosUseCase.Estado;
import cl.primerplayer.identidad.api.AdminUsuariosUseCase.Filtro;
import cl.primerplayer.identidad.api.AdminUsuariosUseCase.Indicadores;
import cl.primerplayer.identidad.api.AdminUsuariosUseCase.UsuarioAdmin;
import cl.primerplayer.shared.api.FechasTienda;
import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.Rol;
import cl.primerplayer.shared.api.UsuarioActual;
import cl.primerplayer.shared.api.ValidacionException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** Panel de usuarios (solo ADMIN). Contrato del frontend: docs/api-admin-usuarios.md (repo del front). */
@RestController
@RequestMapping("/api/v1/admin/usuarios")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin · Usuarios")
@SecurityRequirement(name = "bearer")
class AdminUsuariosController {

    private final AdminUsuariosUseCase usuarios;

    @GetMapping
    @Operation(summary = "Buscar usuarios (más recientes primero)")
    Pagina<UsuarioAdminResponse> buscar(
            @Parameter(description = "nombre (sin tildes), correo o id") @RequestParam(required = false) String q,
            @Parameter(description = "usuario | vendedor | admin; repetible") @RequestParam(required = false) List<String> rol,
            @Parameter(description = "activo | baneado | lista-negra | sin-verificar; repetible (OR)")
            @RequestParam(required = false) List<String> estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Set<Rol> roles = rol == null ? Set.of() : rol.stream().map(Rol::deFrontend).collect(Collectors.toSet());
        return usuarios.buscar(new Filtro(q, roles, estados(estado), page, size)).map(UsuarioAdminResponse::de);
    }

    @GetMapping("/indicadores")
    @Operation(summary = "{total, clientes, internos, baneados, listaNegra, sinVerificar}")
    Indicadores indicadores() {
        return usuarios.indicadores();
    }

    @PatchMapping("/{id}/rol")
    @Operation(summary = "Cambiar el rol", description = "409 self-action / last-admin.")
    UsuarioAdminResponse rol(@PathVariable long id, @Valid @RequestBody RolRequest req, @AuthenticationPrincipal UsuarioActual a) {
        return UsuarioAdminResponse.de(usuarios.cambiarRol(a.id(), id, Rol.deFrontend(req.rol())));
    }

    @PostMapping("/{id}/baneo")
    @Operation(summary = "Banear (no puede iniciar sesión)", description = "hasta: ISO o null = permanente. Cierra sus sesiones.")
    UsuarioAdminResponse banear(@PathVariable long id, @Valid @RequestBody BaneoRequest req, @AuthenticationPrincipal UsuarioActual a) {
        return UsuarioAdminResponse.de(usuarios.banear(a.id(), id, req.motivo(), FechasTienda.parse("hasta", req.hasta())));
    }

    @DeleteMapping("/{id}/baneo")
    @Operation(summary = "Quitar el baneo")
    UsuarioAdminResponse quitarBaneo(@PathVariable long id, @AuthenticationPrincipal UsuarioActual a) {
        return UsuarioAdminResponse.de(usuarios.quitarBaneo(a.id(), id));
    }

    @PostMapping("/{id}/lista-negra")
    @Operation(summary = "Poner en lista negra (puede entrar, pero no comprar ni solicitar cumpleaños)")
    UsuarioAdminResponse listaNegra(@PathVariable long id, @Valid @RequestBody MotivoRequest req, @AuthenticationPrincipal UsuarioActual a) {
        return UsuarioAdminResponse.de(usuarios.ponerEnListaNegra(a.id(), id, req.motivo()));
    }

    @DeleteMapping("/{id}/lista-negra")
    @Operation(summary = "Quitar de la lista negra")
    UsuarioAdminResponse quitarListaNegra(@PathVariable long id, @AuthenticationPrincipal UsuarioActual a) {
        return UsuarioAdminResponse.de(usuarios.quitarDeListaNegra(a.id(), id));
    }

    @PostMapping("/{id}/restablecimiento-password")
    @Operation(summary = "Enviar correo para restablecer la contraseña y exigir el cambio")
    UsuarioAdminResponse restablecer(@PathVariable long id, @AuthenticationPrincipal UsuarioActual a) {
        return UsuarioAdminResponse.de(usuarios.solicitarCambioPassword(a.id(), id));
    }

    @PostMapping("/{id}/verificacion")
    @Operation(summary = "Reenviar el correo de verificación", description = "409 already-verified.")
    UsuarioAdminResponse verificacion(@PathVariable long id, @AuthenticationPrincipal UsuarioActual a) {
        return UsuarioAdminResponse.de(usuarios.reenviarVerificacion(a.id(), id));
    }

    @PostMapping("/{id}/cierre-sesiones")
    @Operation(summary = "Cerrar todas sus sesiones", description = "El access token vigente expira solo (≤ 15 min).")
    UsuarioAdminResponse cerrarSesiones(@PathVariable long id, @AuthenticationPrincipal UsuarioActual a) {
        return UsuarioAdminResponse.de(usuarios.cerrarSesiones(a.id(), id));
    }

    private static Set<Estado> estados(List<String> valores) {
        if (valores == null) {
            return Set.of();
        }
        return valores.stream().filter(v -> !v.isBlank()).map(v -> {
            try {
                return Estado.valueOf(v.strip().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException e) {
                throw new ValidacionException("estado", "Use activo, baneado, lista-negra o sin-verificar.");
            }
        }).collect(Collectors.toSet());
    }

    // ---------- DTO (forma del contrato del frontend) ----------

    record RolRequest(@Schema(example = "vendedor") @NotBlank String rol) {}

    record MotivoRequest(@NotBlank @Size(max = 300) String motivo) {}

    record BaneoRequest(@NotBlank @Size(max = 300) String motivo,
                        @Schema(description = "ISO o null = permanente", example = "2026-12-01T00:00:00-03:00") String hasta) {}

    record UsuarioAdminResponse(long id, String email, String nombre,
                                @Schema(description = "usuario | vendedor | admin") String rol,
                                String telefono, boolean emailVerificado, BaneoResponse baneo, ListaNegraResponse listaNegra,
                                boolean cambioPasswordPendiente, OffsetDateTime creadoEn, OffsetDateTime ultimoAcceso) {

        static UsuarioAdminResponse de(UsuarioAdmin u) {
            return new UsuarioAdminResponse(u.id(), u.email(), u.nombre(), u.rol().frontend(), u.telefono(),
                    u.emailVerificado(),
                    u.baneo() == null ? null : new BaneoResponse(u.baneo().motivo(), FechasTienda.enZona(u.baneo().hasta()),
                            FechasTienda.enZona(u.baneo().creadoEn())),
                    u.listaNegra() == null ? null : new ListaNegraResponse(u.listaNegra().motivo(),
                            FechasTienda.enZona(u.listaNegra().creadoEn())),
                    u.cambioPasswordPendiente(), FechasTienda.enZona(u.creadoEn()), FechasTienda.enZona(u.ultimoAcceso()));
        }
    }

    record BaneoResponse(String motivo, OffsetDateTime hasta, OffsetDateTime creadoEn) {}

    record ListaNegraResponse(String motivo, OffsetDateTime creadoEn) {}
}
