package cl.primerplayer.identidad.infrastructure.security;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import cl.primerplayer.identidad.application.port.out.EmisorTokenAcceso;
import cl.primerplayer.identidad.domain.Usuario;
import lombok.RequiredArgsConstructor;

/** Adaptador de salida: JWT de acceso con {@code sub} = id de usuario y claims {@code email} y {@code rol}. */
@Component
@RequiredArgsConstructor
class JwtEmisorTokenAcceso implements EmisorTokenAcceso {

    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_ROL = "rol";

    private final JwtEncoder encoder;
    private final JwtProperties props;

    @Override
    public TokenAcceso emitir(Usuario usuario, Instant ahora) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.emisor())
                .subject(String.valueOf(usuario.id()))
                .issuedAt(ahora)
                .expiresAt(ahora.plus(props.ttl()))
                .claim(CLAIM_EMAIL, usuario.email())
                .claim(CLAIM_ROL, usuario.rol().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String jwt = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenAcceso(jwt, props.ttl().toSeconds());
    }
}
