package cl.primerplayer.identidad.application.port.out;

/** Puerto de salida: valida el ID token de Google (firma, emisor, audiencia y vigencia). Hace HTTP: fuera de transacciones. */
public interface VerificadorGoogle {

    /**
     * @throws cl.primerplayer.identidad.api.GoogleTokenInvalidoException token inválido, vencido o de otra aplicación
     * @throws cl.primerplayer.identidad.api.GoogleNoDisponibleException  sin client id o sin acceso a las llaves de Google
     */
    IdentidadGoogle verificar(String idToken);

    /** Client id configurado (público); vacío = Google deshabilitado. */
    java.util.Optional<String> clientId();

    /** @param sub identificador estable de la cuenta de Google (el correo puede cambiar) */
    record IdentidadGoogle(String sub, String email, boolean emailVerificado, String nombre) {
        @Override
        public String toString() {
            return "IdentidadGoogle[sub=" + sub + "]";
        }
    }
}
