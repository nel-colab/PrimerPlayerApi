package cl.primerplayer.identidad.infrastructure.security;

import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import cl.primerplayer.shared.api.Rol;
import cl.primerplayer.shared.api.UsuarioActual;

/** Convierte el JWT validado en una autenticación cuyo principal es {@link UsuarioActual} y su rol {@code ROLE_*}. */
class UsuarioActualConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Rol rol = Rol.valueOf(jwt.getClaimAsString(JwtEmisorTokenAcceso.CLAIM_ROL));
        UsuarioActual usuario = new UsuarioActual(Long.parseLong(jwt.getSubject()),
                jwt.getClaimAsString(JwtEmisorTokenAcceso.CLAIM_EMAIL), rol);
        return new UsernamePasswordAuthenticationToken(usuario, jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
    }
}
