package cl.primerplayer.identidad.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(example = "ash@pueblopaleta.cl") @NotBlank @Size(max = 254) String email,
        @Schema(example = "Pikachu2026!") @NotBlank @Size(max = 200) String password) {

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
