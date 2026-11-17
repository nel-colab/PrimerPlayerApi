package cl.primerplayer.identidad.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * TODO(pregunta-abierta): política de contraseña (largo mínimo, complejidad, lista de contraseñas filtradas).
 * Por ahora: 8 a 72 caracteres.
 */
public record RegistroRequest(
        @Schema(example = "Ash Ketchum") @NotBlank @Size(max = 120) String nombre,
        @Schema(example = "ash@pueblopaleta.cl") @NotBlank @Email @Size(max = 254) String email,
        @Schema(example = "Pikachu2026!") @NotBlank @Size(min = 8, max = 72) String password) {

    @Override
    public String toString() {
        return "RegistroRequest[nombre=" + nombre + ", email=" + email + "]";
    }
}
