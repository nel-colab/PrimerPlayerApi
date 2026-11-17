package cl.primerplayer.media.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import cl.primerplayer.media.application.port.out.ArchivoRepositorio;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class ArchivoJdbcAdapter implements ArchivoRepositorio {

    private final JdbcTemplate jdbc;

    @Override
    public void insertar(Fila f) {
        jdbc.update("""
                INSERT INTO archivo (id, bucket, clave, mime, bytes, ancho, alto, sha256, subido_por, creado_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, f.id(), f.bucket(), f.clave(), f.mime(), f.bytes(), f.ancho(), f.alto(), f.sha256(), f.subidoPor(),
                f.creadoEn().atOffset(ZoneOffset.UTC));
    }

    @Override
    public Optional<Fila> porId(UUID id) {
        return jdbc.query("SELECT * FROM archivo WHERE id = ?", (rs, n) -> fila(rs), id).stream().findFirst();
    }

    @Override
    public List<Fila> porIds(Collection<UUID> ids) {
        return jdbc.query("SELECT * FROM archivo WHERE id = ANY (?)", (rs, n) -> fila(rs),
                (Object) ids.toArray(UUID[]::new));
    }

    private static Fila fila(ResultSet rs) throws SQLException {
        return new Fila(rs.getObject("id", UUID.class), rs.getString("bucket"), rs.getString("clave"),
                rs.getString("mime"), rs.getInt("bytes"), (Integer) rs.getObject("ancho"), (Integer) rs.getObject("alto"),
                rs.getString("sha256"), (Long) rs.getObject("subido_por"),
                rs.getObject("creado_en", OffsetDateTime.class).toInstant());
    }
}
