package cl.primerplayer.identidad.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Código de un solo uso (verificación de correo o recuperación de contraseña). {@code tokenHash} es
 * {@link CodigoVerificacion#hash}; {@code creadoEn} viene del {@code Clock} (cuenta para el tope de intentos por hora).
 */
public record TokenUnUso(UUID id, long usuarioId, Tipo tipo, String tokenHash, Instant creadoEn, Instant expiraEn) {

    public enum Tipo { VERIFICACION, RECUPERACION }
}
