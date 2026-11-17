package cl.primerplayer.identidad.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.identidad.api.CredencialesInvalidasException;
import cl.primerplayer.identidad.api.CuentaBloqueadaException;
import cl.primerplayer.identidad.api.IniciarSesionUseCase;
import cl.primerplayer.identidad.application.port.out.EmisorTokenAcceso;
import cl.primerplayer.identidad.application.port.out.EmisorTokenAcceso.TokenAcceso;
import cl.primerplayer.identidad.application.port.out.HasherPassword;
import cl.primerplayer.identidad.application.port.out.RefreshTokenRepositorio;
import cl.primerplayer.identidad.application.port.out.UsuarioRepositorio;
import cl.primerplayer.identidad.domain.RefreshToken;
import cl.primerplayer.identidad.domain.TokenOpaco;
import cl.primerplayer.identidad.domain.Usuario;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class IniciarSesionService implements IniciarSesionUseCase {

    private final UsuarioRepositorio usuarios;
    private final RefreshTokenRepositorio refreshTokens;
    private final HasherPassword hasher;
    private final EmisorTokenAcceso emisor;
    private final IdentidadProperties props;
    private final Clock clock;

    /**
     * Los errores de credenciales no hacen rollback: el contador de intentos fallidos debe quedar guardado
     * aunque la respuesta sea un error.
     */
    @Override
    @Transactional(noRollbackFor = {CredencialesInvalidasException.class, CuentaBloqueadaException.class})
    public SesionIniciada iniciarSesion(IniciarSesionCommand cmd) {
        Instant ahora = clock.instant();
        Usuario usuario = usuarios.buscarPorEmail(Usuario.normalizarEmail(cmd.email()))
                .filter(Usuario::puedeIniciarSesion)
                .orElse(null);

        if (usuario == null) {
            hasher.coincide(cmd.password(), hasher.hashSenuelo());   // mismo costo que un usuario real
            throw new CredencialesInvalidasException();
        }
        if (usuario.estaBloqueado(ahora)) {
            throw new CuentaBloqueadaException(usuario.bloqueadoHasta());
        }
        if (!hasher.coincide(cmd.password(), usuario.passwordHash())) {
            var bloqueo = props.bloqueo();
            Instant hasta = ahora.plus(bloqueo.duracion());
            if (usuarios.registrarIntentoFallido(usuario.id(), bloqueo.maxIntentos(), hasta, ahora)) {
                throw new CuentaBloqueadaException(hasta);
            }
            throw new CredencialesInvalidasException();
        }

        // Baneo de la tienda: se revisa después de la contraseña, así no se revela a un tercero que la cuenta existe.
        var baneo = usuarios.baneoVigente(usuario.id(), ahora);
        if (baneo.isPresent()) {
            throw new cl.primerplayer.identidad.api.CuentaBaneadaException(baneo.get().motivo(), baneo.get().hasta());
        }
        // TODO(pregunta-abierta): ¿se exige correo verificado para iniciar sesión? El contrato define
        // email-not-verified (403) pero no dice dónde aplica; por ahora se permite y se asume que lo exige checkout.
        // TODO: rate limiting por IP/correo con Bucket4j (rate-limited 429 + Retry-After).

        usuarios.registrarInicioExitoso(usuario.id(), ahora);
        TokenAcceso acceso = emisor.emitir(usuario, ahora);
        TokenOpaco refresh = TokenOpaco.generar();
        refreshTokens.guardar(RefreshToken.nuevaFamilia(usuario.id(), refresh, ahora, props.refreshTtl(),
                cmd.ip(), cmd.userAgent()));

        return new SesionIniciada(acceso.valor(), acceso.expiraEnSegundos(), refresh.valor(), props.refreshTtl(),
                UsuarioResumenes.de(usuario));
    }
}
