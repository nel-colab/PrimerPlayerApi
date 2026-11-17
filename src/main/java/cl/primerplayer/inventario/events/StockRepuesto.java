package cl.primerplayer.inventario.events;

/** El stock de un producto pasó de 0 a más de 0. Lo escuchará {@code notificaciones} (alertas de stock por correo). */
public record StockRepuesto(long productoId, int stock) {}
