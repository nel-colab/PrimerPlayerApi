package cl.primerplayer.identidad.web;

import java.time.OffsetDateTime;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import cl.primerplayer.identidad.api.CuentaUseCase;
import cl.primerplayer.identidad.api.CuentaUseCase.Perfil;
import cl.primerplayer.shared.api.FechasTienda;
import cl.primerplayer.shared.api.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Cuenta propia (cualquier rol con sesión) y los enlaces de correo (públicos). El id sale siempre del token.
 * {@code GET /me} vive en {@link MeController} y devuelve este mismo {@link PerfilResponse}.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Cuenta")
class CuentaController {

    private final CuentaUseCase cuenta;

    // ---------- públicos: enlaces que llegan por correo ----------

    @PostMapping("/api/v1/auth/verificacion")
    @Operation(summary = "Verificar el correo con el token del enlace", description = "204. 400 invalid-token si venció o ya se usó.")
    ResponseEntity<Void> verificar(@Valid @RequestBody TokenRequest req) {
        cuenta.verificarEmail(req.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/auth/recuperacion")
    @Operation(summary = "Pedir el enlace para restablecer la contraseña",
            description = "Siempre 202 (no revela si el correo está registrado). El enlace vence en 1 hora.")
    ResponseEntity<Void> recuperar(@Valid @RequestBody EmailRequest req) {
        cuenta.solicitarRecuperacion(req.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/api/v1/auth/restablecimiento")
    @Operation(summary = "Definir una contraseña nueva con el token del enlace",
            description = "204. Cierra todas las sesiones y desbloquea la cuenta. 400 invalid-token / validation.")
    ResponseEntity<Void> restablecer(@Valid @RequestBody RestablecimientoRequest req) {
        cuenta.restablecerPassword(req.token(), req.password());
        return ResponseEntity.noContent().build();
    }

    // ---------- con sesión ----------

    @GetMapping("/api/v1/me/perfil")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Mi perfil (nombre, correo, teléfono, RUT, miembro desde)")
    PerfilResponse perfil(@AuthenticationPrincipal UsuarioActual usuario) {
        return PerfilResponse.de(cuenta.perfil(usuario.id()));
    }

    @PutMapping("/api/v1/me/perfil")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Actualizar mi perfil", description = "El correo no se cambia aquí. RUT con dígito verificador válido.")
    PerfilResponse actualizar(@Valid @RequestBody PerfilRequest req, @AuthenticationPrincipal UsuarioActual usuario) {
        return PerfilResponse.de(cuenta.actualizarPerfil(usuario.id(), req.nombre(), req.telefono(), req.rut()));
    }

    @PutMapping("/api/v1/me/password")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Cambiar mi contraseña", description = "204. Cierra las demás sesiones (deben volver a iniciar sesión).")
    ResponseEntity<Void> password(@Valid @RequestBody PasswordRequest req, @AuthenticationPrincipal UsuarioActual usuario) {
        cuenta.cambiarPassword(usuario.id(), req.actual(), req.nueva());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/me/verificacion")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Reenviar el correo de verificación", description = "202. 409 already-verified.")
    ResponseEntity<Void> reenviar(@AuthenticationPrincipal UsuarioActual usuario) {
        cuenta.reenviarVerificacion(usuario.id());
        return ResponseEntity.accepted().build();
    }

    // ---------- DTO ----------

    record TokenRequest(@NotBlank @Size(max = 200) String token) {
        @Override public String toString() { return "TokenRequest[***]"; }
    }

    record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {}

    record RestablecimientoRequest(@NotBlank @Size(max = 200) String token,
                                   @Schema(example = "NuevaClave2026!") @NotBlank @Size(min = 8, max = 72) String password) {
        @Override public String toString() { return "RestablecimientoRequest[***]"; }
    }

    record PasswordRequest(@NotBlank String actual, @NotBlank @Size(min = 8, max = 72) String nueva) {
        @Override public String toString() { return "PasswordRequest[***]"; }
    }

    record PerfilRequest(@Schema(example = "Ash Ketchum") @NotBlank @Size(max = 120) String nombre,
                         @Schema(example = "+56 9 1234 5678") @Size(max = 20) String telefono,
                         @Schema(example = "12.345.678-5") @Size(max = 12) String rut) {}

    /** Superconjunto de {@code UsuarioResponse} ({@code email}, {@code name}, {@code role}): no rompe al frontend. */
    record PerfilResponse(String email, String name,
                          @Schema(allowableValues = {"usuario", "vendedor", "admin"}) String role,
                          String phone, String rut, OffsetDateTime memberSince, boolean emailVerified,
                          @Schema(description = "El administrador pidió cambiar la contraseña") boolean mustChangePassword) {

        static PerfilResponse de(Perfil p) {
            return new PerfilResponse(p.email(), p.nombre(), p.rol().frontend(), p.telefono(), p.rut(),
                    FechasTienda.enZona(p.miembroDesde()), p.emailVerificado(), p.cambioPasswordPendiente());
        }
    }
}
