package cl.primerplayer.identidad.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import cl.primerplayer.identidad.domain.RefreshToken;

/** Puerto de salida: persistencia de refresh tokens. */
public interface RefreshTokenRepositorio {

    void guardar(RefreshToken token);

    /**
     * Busca por hash y bloquea la fila ({@code SELECT ... FOR UPDATE}): dos refresh simultáneos con el mismo token
     * se serializan y solo uno puede rotarlo.
     */
    Optional<RefreshToken> buscarParaRotar(String tokenHash);

    /** Marca el token como rotado: revocado ahora y apuntando a su sucesor. */
    void marcarRotado(UUID id, UUID sucesor, Instant ahora);

    /** Revoca todos los tokens aún vigentes de la familia. */
    int revocarFamilia(UUID familia, Instant ahora);

    /**
     * Revoca todos los tokens aún vigentes de la familia a la que pertenece el token con ese hash (logout).
     *
     * @return cantidad de tokens revocados (0 si el hash no existe o la familia ya estaba revocada)
     */
    int revocarFamiliaDe(String tokenHash, Instant ahora);
}
