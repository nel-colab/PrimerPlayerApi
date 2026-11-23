package cl.primerplayer.identidad.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import cl.primerplayer.identidad.api.GoogleUseCase;
import cl.primerplayer.identidad.web.CuentaController.PerfilResponse;
import cl.primerplayer.identidad.web.dto.LoginResponse;
import cl.primerplayer.shared.api.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** "Continuar con Google": el frontend envía el {@code credential} (ID token) que entrega Google Identity Services. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Autenticación")
class GoogleController {

    private final GoogleUseCase google;
    private final IdentidadWebMapper mapper;
    private final RefreshCookie refreshCookie;

    @GetMapping("/api/v1/auth/google/config")
    @Operation(summary = "Client id de Google para el botón del frontend",
        description = "Público. enabled = false si el backend no tiene client id (el botón avisa que no está disponible).")
    GoogleConfigResponse config() {
        return google.clientId().map(id -> new GoogleConfigResponse(id, true)).orElse(new GoogleConfigResponse("", false));
    }

    @PostMapping("/api/v1/auth/google")
    @Operation(summary = "Iniciar sesión (o registrarse) con Google",
        description = "Igual que el login: access token en el cuerpo y refresh en la cookie pp_rt. Si el correo ya tiene "
                + "cuenta la vincula y marca el correo como verificado; si no existe crea una cuenta de cliente sin "
                + "contraseña (puede definirla luego con la recuperación de contraseña).",
        responses = {
            @ApiResponse(responseCode = "200", description = "Sesión iniciada"),
            @ApiResponse(responseCode = "401", description = "invalid-google-token"),
            @ApiResponse(responseCode = "423", description = "account-banned"),
            @ApiResponse(responseCode = "409", description = "google-account-in-use"),
            @ApiResponse(responseCode = "503", description = "google-unavailable")})
    ResponseEntity<LoginResponse> login(@Valid @RequestBody GoogleRequest req,
                                        @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                        HttpServletRequest http) {
        var sesion = google.iniciarSesion(req.credential(), http.getRemoteAddr(), userAgent);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.crear(sesion.refreshToken(), sesion.refreshTtl()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(mapper.toResponse(sesion));
    }

    @PostMapping("/api/v1/me/verificacion/google")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Verificar mi correo con Google",
        description = "Alternativa al enlace por correo: Google confirma que el correo de la cuenta es tuyo y la vincula "
                + "(después puedes entrar con Google). Devuelve el perfil con emailVerified = true.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Correo verificado"),
            @ApiResponse(responseCode = "401", description = "invalid-google-token"),
            @ApiResponse(responseCode = "409", description = "google-email-mismatch | google-account-in-use"),
            @ApiResponse(responseCode = "503", description = "google-unavailable")})
    PerfilResponse verificar(@Valid @RequestBody GoogleRequest req, @AuthenticationPrincipal UsuarioActual usuario) {
        return PerfilResponse.de(google.verificarCorreo(usuario.id(), req.credential()));
    }

    /** El client id es público (va en el HTML de cualquier sitio con el botón de Google). */
    record GoogleConfigResponse(String clientId, boolean enabled) {}

    record GoogleRequest(@Schema(description = "ID token (JWT) entregado por Google Identity Services")
                         @NotBlank @Size(max = 4096) String credential) {
        @Override public String toString() { return "GoogleRequest[***]"; }
    }
}
