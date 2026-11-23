package cl.primerplayer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import cl.primerplayer.shared.api.Rol;

/** Crea un usuario real (para las FK como {@code movimiento_stock.usuario_id}) y le emite un JWT válido. */
public final class TokensDePrueba {

    private TokensDePrueba() {}

    public record UsuarioConToken(long id, String email, String token) {
        public String bearer() { return "Bearer " + token; }
    }

    public static UsuarioConToken crear(JdbcTemplate jdbc, JwtEncoder encoder, Rol rol) {
        String email = rol.name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@primerplayer.test";
        // Perfil completo (nombre, teléfono, RUT): el personal lo necesita para usar /admin/**
        Long id = jdbc.queryForObject("""
                INSERT INTO usuario (email, nombre, rol, email_verificado_en, telefono, rut)
                VALUES (?, ?, ?, now(), '+56 9 1234 5678', '11111111-1') RETURNING id""",
                Long.class, email, "Prueba " + rol, rol.name());
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("primer-player-api")
                .subject(String.valueOf(id))
                .issuedAt(ahora)
                .expiresAt(ahora.plus(15, ChronoUnit.MINUTES))
                .claim("email", email)
                .claim("rol", rol.name())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new UsuarioConToken(id, email, token);
    }

    /** EAN-13 válido aleatorio (12 dígitos + verificador). */
    public static String ean13() {
        StringBuilder sb = new StringBuilder("78");
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        while (sb.length() < 12) sb.append(r.nextInt(10));
        int suma = 0;
        for (int i = 11, peso = 3; i >= 0; i--, peso = 4 - peso) suma += (sb.charAt(i) - '0') * peso;
        return sb.append((10 - suma % 10) % 10).toString();
    }
}
