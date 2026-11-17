package cl.primerplayer.identidad.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token de una sesión. Cada login abre una familia nueva; cada refresh rota el token dentro de la familia
 * (el anterior queda revocado con {@code reemplazadoPor}). Presentar un token ya rotado = reúso → se revoca la familia.
 */
public record RefreshToken(UUID id, long usuarioId, UUID familia, String tokenHash, Instant expiraEn,
                           Instant creadoEn, String ip, String userAgent, Instant revocadoEn, UUID reemplazadoPor) {

    /** Resultado de evaluar un token presentado en {@code /auth/refresh}. */
    public enum Estado {
        /** Se puede rotar. */
        VIGENTE,
        /** Venció o se revocó (logout, cambio de contraseña): 401 sin más efectos. */
        INVALIDO,
        /** Ya fue rotado hace menos que la gracia: carrera entre pestañas, 401 sin revocar la familia. */
        ROTADO_RECIENTE,
        /** Ya fue rotado antes de la gracia: posible robo, 401 y se revoca toda la familia. */
        REUSO
    }

    public static RefreshToken nuevaFamilia(long usuarioId, TokenOpaco token, Instant ahora, Duration ttl,
                                            String ip, String userAgent) {
        return new RefreshToken(UUID.randomUUID(), usuarioId, UUID.randomUUID(), token.hash(), ahora.plus(ttl),
                ahora, ip, truncar(userAgent, 255), null, null);
    }

    /**
     * Token sucesor en la misma familia. TODO(pregunta-abierta): la expiración es deslizante (cada refresh renueva
     * {@code ttl}); si se quiere una vida máxima absoluta de la sesión, heredar el vencimiento de la familia.
     */
    public RefreshToken sucesor(TokenOpaco nuevo, Instant ahora, Duration ttl, String ip, String userAgent) {
        return new RefreshToken(UUID.randomUUID(), usuarioId, familia, nuevo.hash(), ahora.plus(ttl), ahora, ip,
                truncar(userAgent, 255), null, null);
    }

    public Estado evaluar(Instant ahora, Duration gracia) {
        if (revocadoEn != null) {
            if (reemplazadoPor == null) {
                return Estado.INVALIDO;   // revocado por logout o por reúso previo
            }
            return revocadoEn.plus(gracia).isAfter(ahora) ? Estado.ROTADO_RECIENTE : Estado.REUSO;
        }
        return expiraEn.isAfter(ahora) ? Estado.VIGENTE : Estado.INVALIDO;
    }

    private static String truncar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
