package cl.primerplayer.identidad.application.port.out;

import java.time.Instant;
import java.util.Optional;

import cl.primerplayer.identidad.domain.TokenUnUso;
import cl.primerplayer.shared.api.Rol;

/** Puerto de salida: datos de la cuenta y operaciones de seguridad sobre ella (perfil, contraseña, tokens, sesiones). */
public interface CuentaRepositorio {

    /** Cuenta no eliminada y no anónima. */
    Optional<FilaCuenta> porId(long usuarioId);

    void actualizarPerfil(long usuarioId, String nombre, String telefono, String rut, Instant ahora);

    /**
     * Marca el token como usado si es del tipo, no se usó y no venció (UPDATE atómico: un enlace sirve una sola vez).
     *
     * @return el usuario dueño del token
     */
    Optional<Long> consumirToken(String tokenHash, TokenUnUso.Tipo tipo, Instant ahora);

    void marcarVerificado(long usuarioId, Instant ahora);

    /** Nueva contraseña; quita el cambio pendiente y desbloquea (intentos fallidos a 0). */
    void cambiarPassword(long usuarioId, String passwordHash, Instant ahora);

    /** Revoca todos los refresh tokens vigentes del usuario. @return cuántos */
    int revocarSesiones(long usuarioId, Instant ahora);

    record FilaCuenta(long id, String email, String nombre, String passwordHash, Rol rol, boolean activo,
                      String telefono, String rut, Instant creadoEn, Instant emailVerificadoEn,
                      boolean cambioPasswordPendiente) {}
}
