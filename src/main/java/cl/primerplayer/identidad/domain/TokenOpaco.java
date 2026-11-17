package cl.primerplayer.identidad.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Token aleatorio de 256 bits. En la base solo se guarda {@link #hash()} (SHA-256 en hex, 64 caracteres). */
public record TokenOpaco(String valor, String hash) {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static TokenOpaco generar() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String valor = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new TokenOpaco(valor, hashDe(valor));
    }

    public static String hashDe(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    @Override
    public String toString() {
        return "TokenOpaco[***]";
    }
}
