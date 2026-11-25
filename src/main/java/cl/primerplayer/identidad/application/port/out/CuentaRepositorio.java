package cl.primerplayer.identidad.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import cl.primerplayer.identidad.domain.TokenUnUso;
import cl.primerplayer.shared.api.Rol;

/** Puerto de salida: datos de la cuenta y operaciones de seguridad sobre ella (perfil, contraseña, tokens, sesiones). */
public interface CuentaRepositorio {

    /** Cuenta no eliminada y no anónima. */
    Optional<FilaCuenta> porId(long usuarioId);

    void actualizarPerfil(long usuarioId, String nombre, String telefono, String rut, Instant ahora);

    /** Id de la cuenta con ese correo (no eliminada ni anónima). */
    Optional<Long> idPorEmail(String email);

    /**
     * Código pendiente más reciente de la cuenta (no usado, no vencido), bloqueado ({@code FOR UPDATE}) hasta el fin de
     * la transacción: dos intentos simultáneos se serializan y cuentan bien.
     */
    Optional<CodigoPendiente> bloquearCodigoPendiente(long usuarioId, TokenUnUso.Tipo tipo, Instant ahora);

    /** Suma un intento errado; al llegar a {@code maxIntentos} el código vence. */
    void registrarIntentoFallido(UUID tokenId, int maxIntentos, Instant ahora);

    void marcarUsado(UUID tokenId, Instant ahora);

    /** Intentos errados de la cuenta con códigos de ese tipo emitidos desde {@code desde}. */
    int intentosFallidosDesde(long usuarioId, TokenUnUso.Tipo tipo, Instant desde);

    record CodigoPendiente(UUID id, String hash) {}

    void marcarVerificado(long usuarioId, Instant ahora);

    /** Nueva contraseña; quita el cambio pendiente y desbloquea (intentos fallidos a 0). */
    void cambiarPassword(long usuarioId, String passwordHash, Instant ahora);

    /** Revoca todos los refresh tokens vigentes del usuario. @return cuántos */
    int revocarSesiones(long usuarioId, Instant ahora);

    record FilaCuenta(long id, String email, String nombre, String passwordHash, Rol rol, boolean activo,
                      String telefono, String rut, Instant creadoEn, Instant emailVerificadoEn,
                      boolean cambioPasswordPendiente) {}
}
