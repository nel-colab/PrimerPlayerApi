package cl.primerplayer.shared.api;

/** Principal autenticado (sale del JWT). Úsalo con {@code @AuthenticationPrincipal}. */
public record UsuarioActual(long id, String email, Rol rol) {}
