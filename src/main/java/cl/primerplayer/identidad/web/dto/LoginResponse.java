package cl.primerplayer.identidad.web.dto;

/** {@code expiresIn} en segundos. El refresh token no va aquí: viaja en la cookie HttpOnly {@code pp_rt}. */
public record LoginResponse(String accessToken, long expiresIn, UsuarioResponse user) {

    @Override
    public String toString() {
        return "LoginResponse[user=" + user + "]";
    }
}
