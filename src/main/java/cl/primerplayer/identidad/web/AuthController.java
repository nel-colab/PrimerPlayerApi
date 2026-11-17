package cl.primerplayer.identidad.web;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.primerplayer.identidad.api.CerrarSesionUseCase;
import cl.primerplayer.identidad.api.IniciarSesionUseCase;
import cl.primerplayer.identidad.api.IniciarSesionUseCase.IniciarSesionCommand;
import cl.primerplayer.identidad.api.IniciarSesionUseCase.SesionIniciada;
import cl.primerplayer.identidad.api.OrigenNoPermitidoException;
import cl.primerplayer.identidad.api.RefrescarSesionUseCase;
import cl.primerplayer.identidad.api.RefrescarSesionUseCase.RefrescarSesionCommand;
import cl.primerplayer.identidad.api.RegistrarUsuarioUseCase;
import cl.primerplayer.identidad.web.dto.LoginRequest;
import cl.primerplayer.identidad.web.dto.LoginResponse;
import cl.primerplayer.identidad.web.dto.RegistroRequest;
import cl.primerplayer.identidad.web.dto.UsuarioResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Adaptador de entrada REST: delgado, valida, delega en los casos de uso y mapea. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación")
class AuthController {

    private final RegistrarUsuarioUseCase registrarUsuario;
    private final IniciarSesionUseCase iniciarSesion;
    static final String HEADER_ORIGEN = "X-Requested-With";
    static final String VALOR_ORIGEN = "pp-web";

    private final CerrarSesionUseCase cerrarSesion;
    private final RefrescarSesionUseCase refrescarSesion;
    private final IdentidadWebMapper mapper;
    private final RefreshCookie refreshCookie;

    @PostMapping("/registro")
    @Operation(summary = "Registrar un cliente", responses = {
        @ApiResponse(responseCode = "201", description = "Cuenta creada; se envía correo de verificación"),
        @ApiResponse(responseCode = "400", description = "validation"),
        @ApiResponse(responseCode = "409", description = "email-taken")})
    ResponseEntity<UsuarioResponse> registrar(@Valid @RequestBody RegistroRequest request) {
        var usuario = registrarUsuario.registrar(mapper.toCommand(request));
        return ResponseEntity.created(URI.create("/api/v1/me")).body(mapper.toResponse(usuario));
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "Devuelve el access token y deja el refresh en la cookie pp_rt",
        responses = {
            @ApiResponse(responseCode = "200", description = "Sesión iniciada"),
            @ApiResponse(responseCode = "400", description = "validation"),
            @ApiResponse(responseCode = "401", description = "unauthorized"),
            @ApiResponse(responseCode = "423", description = "account-locked")})
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                        @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                        HttpServletRequest http) {
        // TODO: detrás del proxy de producción, getRemoteAddr() requiere server.forward-headers-strategy.
        SesionIniciada sesion = iniciarSesion.iniciarSesion(
                new IniciarSesionCommand(request.email(), request.password(), http.getRemoteAddr(), userAgent));
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.crear(sesion.refreshToken(), sesion.refreshTtl()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(mapper.toResponse(sesion));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar el access token",
        description = "Usa la cookie pp_rt y exige X-Requested-With: pp-web. Rota el refresh (nueva cookie). "
                + "Un refresh ya usado revoca la sesión de ese dispositivo.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Igual que el login: {accessToken, expiresIn, user}"),
            @ApiResponse(responseCode = "401", description = "unauthorized: volver a iniciar sesión"),
            @ApiResponse(responseCode = "403", description = "forbidden: falta X-Requested-With: pp-web")})
    ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = RefreshCookie.NOMBRE, required = false) String refreshToken,
            @RequestHeader(value = HEADER_ORIGEN, required = false) String origen,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
            HttpServletRequest http) {
        // La cookie viaja sola en cualquier petición al dominio: el header propio (que un formulario de otro sitio
        // no puede enviar sin preflight CORS) prueba que la llamada viene de nuestro front. Complementa SameSite=Strict.
        if (!VALOR_ORIGEN.equals(origen)) {
            throw new OrigenNoPermitidoException();
        }
        SesionIniciada sesion = refrescarSesion.refrescar(
                new RefrescarSesionCommand(refreshToken, http.getRemoteAddr(), userAgent));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.crear(sesion.refreshToken(), sesion.refreshTtl()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(mapper.toResponse(sesion));
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesión",
        description = "Revoca la familia del refresh token de la cookie pp_rt y borra la cookie. Idempotente.",
        responses = @ApiResponse(responseCode = "204", description = "Sesión cerrada (o no había sesión)"))
    ResponseEntity<Void> logout(@CookieValue(name = RefreshCookie.NOMBRE, required = false) String refreshToken) {
        cerrarSesion.cerrarSesion(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.borrar().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}
