package cl.primerplayer.identidad.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.primerplayer.identidad.api.CredencialesInvalidasException;
import cl.primerplayer.identidad.api.CuentaUseCase;
import cl.primerplayer.identidad.web.CuentaController.PerfilResponse;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import cl.primerplayer.shared.api.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Autenticación")
@SecurityRequirement(name = "bearer")
class MeController {

    private final CuentaUseCase cuenta;

    /** El id sale siempre del token, nunca del request. */
    @GetMapping
    @Operation(summary = "Usuario de la sesión actual",
            description = "email, name y role (como siempre) + phone, rut, memberSince, emailVerified y mustChangePassword.")
    PerfilResponse yo(@AuthenticationPrincipal UsuarioActual actual) {
        try {
            return PerfilResponse.de(cuenta.perfil(actual.id()));
        } catch (RecursoNoEncontradoException e) {
            throw new CredencialesInvalidasException();   // desactivado o eliminado tras emitir el token
        }
    }
}
