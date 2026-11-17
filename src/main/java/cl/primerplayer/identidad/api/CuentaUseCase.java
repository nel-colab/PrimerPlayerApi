package cl.primerplayer.identidad.api;

import java.time.Instant;

import cl.primerplayer.shared.api.Rol;

/** Puerto de entrada: la cuenta del propio usuario (perfil, contraseña, verificación y recuperación). */
public interface CuentaUseCase {

    Perfil perfil(long usuarioId);

    /**
     * @param telefono opcional (dígitos, espacios y +; máx. 20)
     * @param rut      opcional; con dígito verificador válido (acepta puntos y guion)
     * @throws cl.primerplayer.shared.api.ValidacionException datos inválidos
     */
    Perfil actualizarPerfil(long usuarioId, String nombre, String telefono, String rut);

    /**
     * Cambia la contraseña y cierra las demás sesiones (revoca todos los refresh tokens).
     *
     * @throws cl.primerplayer.shared.api.ValidacionException si la actual no coincide o la nueva no cumple la política
     */
    void cambiarPassword(long usuarioId, String actual, String nueva);

    /** @throws TokenInvalidoException 400 {@code invalid-token} si no existe, venció o ya se usó */
    void verificarEmail(String token);

    /** @throws YaVerificadoException 409 {@code already-verified} */
    void reenviarVerificacion(long usuarioId);

    /** Siempre "acepta" (202): no revela si el correo existe. Si existe, envía el enlace de restablecimiento. */
    void solicitarRecuperacion(String email);

    /**
     * Define una contraseña nueva con el enlace de recuperación; cierra todas las sesiones y desbloquea la cuenta.
     *
     * @throws TokenInvalidoException 400 {@code invalid-token}
     */
    void restablecerPassword(String token, String nueva);

    record Perfil(long id, String email, String nombre, Rol rol, String telefono, String rut, Instant miembroDesde,
                  boolean emailVerificado, boolean cambioPasswordPendiente) {}
}
