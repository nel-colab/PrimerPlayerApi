package cl.primerplayer.identidad.api;

/** Puerto de entrada: cierre de sesión a partir del refresh token de la cookie {@code pp_rt}. */
public interface CerrarSesionUseCase {

    /**
     * Revoca la familia completa del refresh token (la sesión de ese dispositivo). Es idempotente:
     * un token nulo, desconocido o ya revocado no produce error.
     *
     * <p>El access token (JWT) sigue siendo válido hasta que expire (máx. {@code pp.auth.jwt.ttl}): la API es
     * sin estado y no mantiene lista negra de JWT.
     */
    void cerrarSesion(String refreshToken);
}
