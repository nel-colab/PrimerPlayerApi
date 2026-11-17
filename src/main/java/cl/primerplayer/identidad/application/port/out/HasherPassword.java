package cl.primerplayer.identidad.application.port.out;

/** Puerto de salida: hash de contraseñas. */
public interface HasherPassword {

    String hashear(String passwordPlano);

    boolean coincide(String passwordPlano, String hash);

    /**
     * Hash válido de una contraseña cualquiera. Se compara contra él cuando el usuario no existe,
     * para que el tiempo de respuesta no revele qué correos están registrados.
     */
    String hashSenuelo();
}
