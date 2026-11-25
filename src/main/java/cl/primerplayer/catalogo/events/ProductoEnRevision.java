package cl.primerplayer.catalogo.events;

import java.time.Instant;

/**
 * Un VENDEDOR dejó algo esperando la aprobación de un ADMIN que conviene revisar pronto: un producto nuevo
 * ({@code productoNuevo}) o un cambio de precio de un producto ya aprobado. Lo escucha {@code notificaciones} para
 * avisar por correo a los administradores.
 *
 * @param precioActual    precio aprobado vigente; {@code null} si el producto es nuevo
 * @param precioPropuesto precio que quedaría al aprobar (en un producto nuevo, su precio)
 */
public record ProductoEnRevision(
        long revisionId,
        long productoId,
        String productoNombre,
        boolean productoNuevo,
        Long precioActual,
        long precioPropuesto,
        long solicitadoPor,
        Instant solicitadoEn) {
}
