package cl.primerplayer.identidad.api;

import java.time.Duration;

/** Puerto de entrada: inicio de sesión con correo y contraseña. */
public interface IniciarSesionUseCase {

    /**
     * @throws CredencialesInvalidasException correo o contraseña incorrectos (mensaje genérico)
     * @throws CuentaBloqueadaException       demasiados intentos fallidos
     */
    SesionIniciada iniciarSesion(IniciarSesionCommand command);

    record IniciarSesionCommand(String email, String password, String ip, String userAgent) {
        @Override
        public String toString() {
            return "IniciarSesionCommand[email=" + email + ", ip=" + ip + "]";
        }
    }

    /** {@code refreshToken} es el valor opaco en claro: solo viaja en la cookie, en la base se guarda su hash. */
    record SesionIniciada(String accessToken, long expiresIn, String refreshToken, Duration refreshTtl,
                          UsuarioResumen usuario) {
        @Override
        public String toString() {
            return "SesionIniciada[usuario=" + usuario + "]";
        }
    }
}
