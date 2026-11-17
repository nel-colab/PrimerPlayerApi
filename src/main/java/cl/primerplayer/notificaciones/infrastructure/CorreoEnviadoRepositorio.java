package cl.primerplayer.notificaciones.infrastructure;

import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** Tabla {@code correo_enviado}: evita enviar dos veces el mismo correo (UNIQUE plantilla + referencia + destino). */
@Component
@RequiredArgsConstructor
public class CorreoEnviadoRepositorio {

    private final JdbcTemplate jdbc;

    /** @return {@code false} si ya estaba registrado (el correo ya se envió) */
    public boolean reservar(String destino, String plantilla, String referencia, Instant ahora) {
        return jdbc.update("""
                INSERT INTO correo_enviado (destino, plantilla, referencia, enviado_en) VALUES (?, ?, ?, ?)
                ON CONFLICT (plantilla, referencia, destino) DO NOTHING
                """, destino, plantilla, referencia, ahora.atOffset(ZoneOffset.UTC)) == 1;
    }

    public void liberar(String destino, String plantilla, String referencia) {
        jdbc.update("DELETE FROM correo_enviado WHERE destino = ? AND plantilla = ? AND referencia = ?",
                destino, plantilla, referencia);
    }
}
