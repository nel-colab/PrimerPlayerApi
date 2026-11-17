package cl.primerplayer.identidad.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Token de un solo uso (verificación de correo o recuperación de contraseña). */
public record TokenUnUso(UUID id, long usuarioId, Tipo tipo, String tokenHash, Instant expiraEn) {

    public enum Tipo { VERIFICACION, RECUPERACION }

    public static TokenUnUso verificacion(long usuarioId, TokenOpaco token, Instant ahora, Duration ttl) {
        return new TokenUnUso(UUID.randomUUID(), usuarioId, Tipo.VERIFICACION, token.hash(), ahora.plus(ttl));
    }

    public static TokenUnUso recuperacion(long usuarioId, TokenOpaco token, Instant ahora, Duration ttl) {
        return new TokenUnUso(UUID.randomUUID(), usuarioId, Tipo.RECUPERACION, token.hash(), ahora.plus(ttl));
    }
}
