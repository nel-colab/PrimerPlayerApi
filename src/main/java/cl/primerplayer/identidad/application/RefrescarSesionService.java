package cl.primerplayer.identidad.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.identidad.api.IniciarSesionUseCase.SesionIniciada;
import cl.primerplayer.identidad.api.RefrescarSesionUseCase;
import cl.primerplayer.identidad.api.SesionInvalidaException;
import cl.primerplayer.identidad.application.port.out.EmisorTokenAcceso;
import cl.primerplayer.identidad.application.port.out.EmisorTokenAcceso.TokenAcceso;
import cl.primerplayer.identidad.application.port.out.RefreshTokenRepositorio;
import cl.primerplayer.identidad.application.port.out.UsuarioRepositorio;
import cl.primerplayer.identidad.domain.RefreshToken;
import cl.primerplayer.identidad.domain.TokenOpaco;
import cl.primerplayer.identidad.domain.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
class RefrescarSesionService implements RefrescarSesionUseCase {

    private final RefreshTokenRepositorio refreshTokens;
    private final UsuarioRepositorio usuarios;
    private final EmisorTokenAcceso emisor;
    private final IdentidadProperties props;
    private final Clock clock;

    /** Sin rollback en 401: la revocación de la familia por reúso debe quedar guardada. */
    @Override
    @Transactional(noRollbackFor = {SesionInvalidaException.class, cl.primerplayer.identidad.api.CuentaBaneadaException.class})
    public SesionIniciada refrescar(RefrescarSesionCommand cmd) {
        if (cmd.refreshToken() == null || cmd.refreshToken().isBlank()) {
            throw new SesionInvalidaException();
        }
        Instant ahora = clock.instant();
        RefreshToken actual = refreshTokens.buscarParaRotar(TokenOpaco.hashDe(cmd.refreshToken()))
                .orElseThrow(SesionInvalidaException::new);

        switch (actual.evaluar(ahora, props.refreshGracia())) {
            case VIGENTE -> { }
            case INVALIDO, ROTADO_RECIENTE -> throw new SesionInvalidaException();
            case REUSO -> {
                // Un token ya rotado volvió a aparecer: alguien más lo tiene. Se cierra la sesión en ese dispositivo.
                int revocados = refreshTokens.revocarFamilia(actual.familia(), ahora);
                log.warn("Reúso de refresh token detectado: usuario={} familia={} revocados={}",
                        actual.usuarioId(), actual.familia(), revocados);
                throw new SesionInvalidaException();
            }
        }

        Usuario usuario = usuarios.buscarPorId(actual.usuarioId()).filter(Usuario::puedeIniciarSesion).orElse(null);
        if (usuario == null) {   // desactivado o eliminado después del login
            refreshTokens.revocarFamilia(actual.familia(), ahora);
            throw new SesionInvalidaException();
        }
        var baneo = usuarios.baneoVigente(usuario.id(), ahora);
        if (baneo.isPresent()) {   // baneado después del login: la sesión se corta y no se renueva
            refreshTokens.revocarFamilia(actual.familia(), ahora);
            throw new cl.primerplayer.identidad.api.CuentaBaneadaException(baneo.get().motivo(), baneo.get().hasta());
        }

        TokenOpaco nuevo = TokenOpaco.generar();
        RefreshToken sucesor = actual.sucesor(nuevo, ahora, props.refreshTtl(), cmd.ip(), cmd.userAgent());
        refreshTokens.guardar(sucesor);
        refreshTokens.marcarRotado(actual.id(), sucesor.id(), ahora);

        TokenAcceso acceso = emisor.emitir(usuario, ahora);
        return new SesionIniciada(acceso.valor(), acceso.expiraEnSegundos(), nuevo.valor(), props.refreshTtl(),
                UsuarioResumenes.de(usuario));
    }
}
