package cl.primerplayer.clientes.events;

/** Un producto que el cliente esperaba volvió a tener stock. Lo escucha notificaciones para avisarle por correo. */
public record AlertaStockDisparada(long usuarioId, String email, String nombre, long productoId, String productoNombre) {}
