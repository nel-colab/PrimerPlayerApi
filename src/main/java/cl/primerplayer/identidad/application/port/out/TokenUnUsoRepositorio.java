package cl.primerplayer.identidad.application.port.out;

import cl.primerplayer.identidad.domain.TokenUnUso;

/** Puerto de salida: persistencia de tokens de un solo uso. */
public interface TokenUnUsoRepositorio {

    void guardar(TokenUnUso token);
}
