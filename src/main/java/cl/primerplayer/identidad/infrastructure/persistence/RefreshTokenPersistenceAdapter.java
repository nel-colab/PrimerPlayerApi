package cl.primerplayer.identidad.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Component;

import cl.primerplayer.identidad.application.port.out.RefreshTokenRepositorio;
import cl.primerplayer.identidad.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class RefreshTokenPersistenceAdapter implements RefreshTokenRepositorio {

    private final RefreshTokenJpaRepository jpa;

    @Override
    public void guardar(RefreshToken t) {
        RefreshTokenEntity e = new RefreshTokenEntity();
        e.setId(t.id());
        e.setUsuarioId(t.usuarioId());
        e.setFamilia(t.familia());
        e.setTokenHash(t.tokenHash());
        e.setExpiraEn(t.expiraEn());
        e.setCreadoEn(t.creadoEn());
        e.setIp(t.ip());
        e.setUserAgent(t.userAgent());
        e.setRevocadoEn(t.revocadoEn());
        e.setReemplazadoPor(t.reemplazadoPor());
        jpa.save(e);
    }

    @Override
    public Optional<RefreshToken> buscarParaRotar(String tokenHash) {
        return jpa.buscarParaActualizar(tokenHash).map(e -> new RefreshToken(e.getId(), e.getUsuarioId(),
                e.getFamilia(), e.getTokenHash(), e.getExpiraEn(), e.getCreadoEn(), e.getIp(), e.getUserAgent(),
                e.getRevocadoEn(), e.getReemplazadoPor()));
    }

    @Override
    public void marcarRotado(UUID id, UUID sucesor, Instant ahora) {
        RefreshTokenEntity e = jpa.findById(id).orElseThrow();   // ya bloqueada y administrada por buscarParaRotar
        e.setRevocadoEn(ahora);
        e.setReemplazadoPor(sucesor);
    }

    @Override
    public int revocarFamilia(UUID familia, Instant ahora) {
        return jpa.revocarFamilia(familia, ahora);
    }

    @Override
    public int revocarFamiliaDe(String tokenHash, Instant ahora) {
        return jpa.revocarFamiliaDe(tokenHash, ahora);
    }
}

interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshTokenEntity t WHERE t.tokenHash = :tokenHash")
    Optional<RefreshTokenEntity> buscarParaActualizar(String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE refresh_token SET revocado_en = :ahora WHERE familia = :familia AND revocado_en IS NULL",
           nativeQuery = true)
    int revocarFamilia(UUID familia, Instant ahora);

    /** Un solo UPDATE: la familia se resuelve en la misma sentencia a partir del hash. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE refresh_token SET revocado_en = :ahora
        WHERE revocado_en IS NULL
          AND familia = (SELECT familia FROM refresh_token WHERE token_hash = :tokenHash)
        """, nativeQuery = true)
    int revocarFamiliaDe(String tokenHash, Instant ahora);
}
