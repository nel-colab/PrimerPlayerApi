package cl.primerplayer.identidad.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Forma que espera el frontend: {@code role} en minúscula ({@code usuario}, {@code vendedor}, {@code admin}). */
public record UsuarioResponse(
        String email,
        String name,
        @Schema(allowableValues = {"usuario", "vendedor", "admin"}) String role) {
}
