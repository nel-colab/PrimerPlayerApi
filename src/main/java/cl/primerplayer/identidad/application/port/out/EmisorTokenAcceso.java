package cl.primerplayer.identidad.application.port.out;

import java.time.Instant;

import cl.primerplayer.identidad.domain.Usuario;

/** Puerto de salida: emite el token de acceso (JWT) de un usuario. */
public interface EmisorTokenAcceso {

    TokenAcceso emitir(Usuario usuario, Instant ahora);

    record TokenAcceso(String valor, long expiraEnSegundos) {
        @Override
        public String toString() {
            return "TokenAcceso[***]";
        }
    }
}
