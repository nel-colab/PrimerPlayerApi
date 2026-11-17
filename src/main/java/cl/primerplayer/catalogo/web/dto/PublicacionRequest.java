package cl.primerplayer.catalogo.web.dto;

import jakarta.validation.constraints.NotNull;

public record PublicacionRequest(@NotNull Boolean publicado) {
}
