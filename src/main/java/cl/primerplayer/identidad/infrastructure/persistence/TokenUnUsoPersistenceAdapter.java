package cl.primerplayer.identidad.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
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
        e.setExpiraEn(t.expiraEn());
        jpa.save(e);
    }
}

interface TokenUnUsoJpaRepository extends JpaRepository<TokenUnUsoEntity, UUID> {
}
