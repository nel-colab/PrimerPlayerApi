package cl.primerplayer.identidad.infrastructure.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import cl.primerplayer.identidad.application.port.out.CuentaRepositorio;
import cl.primerplayer.identidad.domain.TokenUnUso;
import cl.primerplayer.shared.api.Rol;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class CuentaJdbcAdapter implements CuentaRepositorio {

    private final JdbcTemplate jdbc;

    @Override
    public Optional<FilaCuenta> porId(long usuarioId) {
        return jdbc.query("""
                SELECT id, email, nombre, password_hash, rol, activo, telefono, rut, creado_en, email_verificado_en,
                       cambio_password_pendiente
                FROM usuario WHERE id = ? AND eliminado_en IS NULL AND NOT es_anonimo
                """, (rs, n) -> new FilaCuenta(rs.getLong("id"), rs.getString("email"), rs.getString("nombre"),
                        rs.getString("password_hash"), Rol.valueOf(rs.getString("rol")), rs.getBoolean("activo"),
                        rs.getString("telefono"), rs.getString("rut"), instante(rs.getObject("creado_en", OffsetDateTime.class)),
                        instante(rs.getObject("email_verificado_en", OffsetDateTime.class)),
                        rs.getBoolean("cambio_password_pendiente")),
                usuarioId).stream().findFirst();
    }

    @Override
    public void actualizarPerfil(long usuarioId, String nombre, String telefono, String rut, Instant ahora) {
        jdbc.update("UPDATE usuario SET nombre = ?, telefono = ?, rut = ?, actualizado_en = ? WHERE id = ?",
                nombre, telefono, rut, utc(ahora), usuarioId);
    }

    @Override
    public Optional<Long> consumirToken(String tokenHash, TokenUnUso.Tipo tipo, Instant ahora) {
        return jdbc.query("""
                UPDATE token_un_uso SET usado_en = ?
                WHERE token_hash = ? AND tipo = ? AND usado_en IS NULL AND expira_en > ?
                RETURNING usuario_id
                """, (rs, n) -> rs.getLong("usuario_id"), utc(ahora), tokenHash, tipo.name(), utc(ahora))
                .stream().findFirst();
    }

    @Override
    public void marcarVerificado(long usuarioId, Instant ahora) {
        jdbc.update("""
                UPDATE usuario SET email_verificado_en = coalesce(email_verificado_en, ?), actualizado_en = ? WHERE id = ?
                """, utc(ahora), utc(ahora), usuarioId);
    }

    @Override
    public void cambiarPassword(long usuarioId, String passwordHash, Instant ahora) {
        jdbc.update("""
                UPDATE usuario SET password_hash = ?, cambio_password_pendiente = FALSE, intentos_fallidos = 0,
                       bloqueado_hasta = NULL, actualizado_en = ?
                WHERE id = ?
                """, passwordHash, utc(ahora), usuarioId);
    }

    @Override
    public int revocarSesiones(long usuarioId, Instant ahora) {
        return jdbc.update("UPDATE refresh_token SET revocado_en = ? WHERE usuario_id = ? AND revocado_en IS NULL",
                utc(ahora), usuarioId);
    }

    private static OffsetDateTime utc(Instant i) {
        return i.atOffset(ZoneOffset.UTC);
    }

    private static Instant instante(OffsetDateTime o) {
        return o == null ? null : o.toInstant();
    }
}
