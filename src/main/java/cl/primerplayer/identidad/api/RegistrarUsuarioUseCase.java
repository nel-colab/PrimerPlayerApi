package cl.primerplayer.identidad.api;

/** Puerto de entrada: registro público de clientes. */
public interface RegistrarUsuarioUseCase {

    /**
     * Crea un usuario CLIENTE con el correo sin verificar y emite un token de verificación.
     *
     * @throws EmailEnUsoException si el correo ya está registrado
     */
    UsuarioResumen registrar(RegistrarUsuarioCommand command);

    record RegistrarUsuarioCommand(String nombre, String email, String password) {
        @Override
        public String toString() {   // nunca loguear la contraseña
            return "RegistrarUsuarioCommand[nombre=" + nombre + ", email=" + email + "]";
        }
    }
}
