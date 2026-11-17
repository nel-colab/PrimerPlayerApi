package cl.primerplayer.identidad.events;

/**
 * Un administrador cambió algo de una cuenta (rol, baneo, lista negra, sesiones…). Lo escucha auditoría.
 *
 * @param antes / despues JSON con el estado relevante (puede ser {@code null})
 */
public record UsuarioAdministrado(long actorId, long usuarioId, String accion, String antes, String despues,
                                  java.time.Instant ocurridoEn) {}
