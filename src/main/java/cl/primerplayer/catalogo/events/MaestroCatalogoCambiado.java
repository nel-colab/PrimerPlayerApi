package cl.primerplayer.catalogo.events;

import java.time.Instant;

/**
 * Un ADMIN creó, editó o eliminó una categoría o franquicia. Lo escucha {@code auditoria}.
 *
 * @param entidad {@code categoria} o {@code franquicia}
 * @param accion  p. ej. {@code CATEGORIA_CREADA}, {@code FRANQUICIA_ELIMINADA}
 * @param antes   JSON con los datos anteriores ({@code null} al crear)
 * @param despues JSON con los datos nuevos ({@code null} al eliminar)
 */
public record MaestroCatalogoCambiado(long actorId, String entidad, int entidadId, String accion, String antes,
                                      String despues, Instant ocurridoEn) {
}
