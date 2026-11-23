package cl.primerplayer.identidad.application;

import java.time.Instant;

import org.springframework.stereotype.Component;

import cl.primerplayer.identidad.api.CuentaBaneadaException;
import cl.primerplayer.identidad.api.IniciarSesionUseCase.SesionIniciada;
import cl.primerplayer.identidad.application.port.out.EmisorTokenAcceso;
import cl.primerplayer.identidad.application.port.out.EmisorTokenAcceso.TokenAcceso;
import cl.primerplayer.identidad.application.port.out.RefreshTokenRepositorio;
import cl.primerplayer.identidad.application.port.out.UsuarioRepositorio;
import cl.primerplayer.identidad.domain.RefreshToken;
import cl.primerplayer.identidad.domain.TokenOpaco;
import cl.primerplayer.identidad.domain.Usuario;
import lombok.RequiredArgsConstructor;

/**
 * Último paso común de todo inicio de sesión (contraseña o Google), ya autenticado el usuario: revisa el baneo,
 * registra el acceso y emite el access token y una familia nueva de refresh. Corre dentro de la transacción de quien
 * lo llama.
 */
@Component
@RequiredArgsConstructor
class AperturaSesion {

    private final UsuarioRepositorio usuarios;
    private final RefreshTokenRepositorio refreshTokens;
    private final EmisorTokenAcceso emisor;
    private final IdentidadProperties props;

    SesionIniciada abrir(Usuario usuario, String ip, String userAgent, Instant ahora) {
        var baneo = usuarios.baneoVigente(usuario.id(), ahora);
        if (baneo.isPresent()) {
            throw new CuentaBaneadaException(baneo.get().motivo(), baneo.get().hasta());
        }
        usuarios.registrarInicioExitoso(usuario.id(), ahora);
        TokenAcceso acceso = emisor.emitir(usuario, ahora);
        TokenOpaco refresh = TokenOpaco.generar();
        refreshTokens.guardar(RefreshToken.nuevaFamilia(usuario.id(), refresh, ahora, props.refreshTtl(), ip, userAgent));
        return new SesionIniciada(acceso.valor(), acceso.expiraEnSegundos(), refresh.valor(), props.refreshTtl(),
                UsuarioResumenes.de(usuario));
    }
}
