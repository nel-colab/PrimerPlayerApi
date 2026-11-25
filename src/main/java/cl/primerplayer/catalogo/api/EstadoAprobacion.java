package cl.primerplayer.catalogo.api;

/**
 * Estado del alta de un producto. Solo un producto {@link #APROBADO} puede estar publicado (CHECK en la base).
 * Los que crea un VENDEDOR nacen {@link #PENDIENTE}; los que crea un ADMIN, {@link #APROBADO}.
 */
public enum EstadoAprobacion { PENDIENTE, APROBADO, RECHAZADO }
