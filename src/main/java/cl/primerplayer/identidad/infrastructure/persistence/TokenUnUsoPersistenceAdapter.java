package cl.primerplayer.identidad.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;

import cl.primerplayer.identidad.application.port.out.TokenUnUsoRepositorio;
import cl.primerplayer.identidad.domain.TokenUnUso;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class TokenUnUsoPersistenceAdapter implements TokenUnUsoRepositorio {

    private final TokenUnUsoJpaRepository jpa;

    @Override
    public void guardar(TokenUnUso t) {
        TokenUnUsoEntity e = new TokenUnUsoEntity();
        e.setId(t.id());
        e.setUsuarioId(t.usuarioId());
        e.setTipo(t.tipo());
        e.setTokenHash(t.tokenHash());
        e.setCreadoEn(t.creadoEn());
        e.setExpiraEn(t.expiraEn());
        jpa.save(e);
    }

    @Override
    public int invalidarPendientes(long usuarioId, TokenUnUso.Tipo tipo, Instant ahora) {
        return jpa.invalidarPendientes(usuarioId, tipo, ahora);
    }
}

interface TokenUnUsoJpaRepository extends JpaRepository<TokenUnUsoEntity, UUID> {

    /** {@code consumirToken} exige {@code expira_en > ahora}: vencer en {@code ahora} los deja inutilizables. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE TokenUnUsoEntity t SET t.expiraEn = :ahora
            WHERE t.usuarioId = :usuarioId AND t.tipo = :tipo AND t.usadoEn IS NULL AND t.expiraEn > :ahora""")
    int invalidarPendientes(@Param("usuarioId") long usuarioId, @Param("tipo") TokenUnUso.Tipo tipo,
                            @Param("ahora") Instant ahora);
}
