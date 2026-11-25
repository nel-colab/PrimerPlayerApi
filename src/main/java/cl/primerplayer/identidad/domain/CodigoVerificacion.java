package cl.primerplayer.identidad.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Código de 6 dígitos que llega por correo para verificar la cuenta o restablecer la contraseña (los correos no llevan
 * enlaces). En la base solo queda {@link #hash}: SHA-256 de {@code id:código}, con el id del token como sal.
 * <p>Un código corto se protege con intentos limitados (por código y por hora), no con su largo: ver
 * {@code CuentaService}.
 */
public final class CodigoVerificacion {

    public static final int MAX_INTENTOS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CodigoVerificacion() {}

    /** 000000–999999, siempre 6 dígitos. */
    public static String generar() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    public static String hash(UUID tokenId, String codigo) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((tokenId + ":" + codigo).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /** Comparación en tiempo constante (no revela cuántos caracteres coinciden). */
    public static boolean coincide(UUID tokenId, String codigo, String hashGuardado) {
        return MessageDigest.isEqual(hash(tokenId, codigo).getBytes(StandardCharsets.US_ASCII),
                hashGuardado.getBytes(StandardCharsets.US_ASCII));
    }

    /** Lo que la persona escribió, sin espacios ni guiones ("482 913" → "482913"); {@code null} si no son 6 dígitos. */
    public static String normalizar(String escrito) {
        if (escrito == null) {
            return null;
        }
        String limpio = escrito.replaceAll("[\\s-]", "");
        return limpio.matches("\\d{6}") ? limpio : null;
    }
}
