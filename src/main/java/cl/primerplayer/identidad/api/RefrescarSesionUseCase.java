package cl.primerplayer.identidad.api;

import cl.primerplayer.identidad.api.IniciarSesionUseCase.SesionIniciada;

/** Puerto de entrada: renovar el access token con el refresh token de la cookie {@code pp_rt}. */
public interface RefrescarSesionUseCase {

    /**
     * Rota el refresh token (el presentado queda revocado) y emite un access token nuevo.
     *
     * @throws SesionInvalidaException token ausente, desconocido, vencido, revocado o reutilizado (en este último
     *                                 caso además se revoca toda la familia)
     */
    SesionIniciada refrescar(RefrescarSesionCommand command);

    record RefrescarSesionCommand(String refreshToken, String ip, String userAgent) {
        @Override
        public String toString() {
            return "RefrescarSesionCommand[ip=" + ip + "]";
        }
    }
}
