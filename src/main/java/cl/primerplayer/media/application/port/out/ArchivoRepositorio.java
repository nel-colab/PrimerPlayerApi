package cl.primerplayer.media.application.port.out;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida: tabla {@code archivo}. */
public interface ArchivoRepositorio {

    void insertar(Fila fila);

    Optional<Fila> porId(UUID id);

    List<Fila> porIds(Collection<UUID> ids);

    record Fila(UUID id, String bucket, String clave, String mime, int bytes, Integer ancho, Integer alto,
                String sha256, Long subidoPor, Instant creadoEn) {}
}
