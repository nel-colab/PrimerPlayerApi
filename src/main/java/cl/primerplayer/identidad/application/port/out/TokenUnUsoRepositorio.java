package cl.primerplayer.identidad.application.port.out;

import java.time.Instant;

import cl.primerplayer.identidad.domain.TokenUnUso;

/** Puerto de salida: persistencia de tokens de un solo uso (solo su hash). */
public interface TokenUnUsoRepositorio {

    void guardar(TokenUnUso token);

    /** Vence ahora los tokens del tipo que el usuario aún no usó. @return cuántos */
    int invalidarPendientes(long usuarioId, TokenUnUso.Tipo tipo, Instant ahora);
}
