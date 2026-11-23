package cl.primerplayer.identidad.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.identidad.api.CredencialesInvalidasException;
import cl.primerplayer.identidad.api.CuentaBloqueadaException;
import cl.primerplayer.identidad.api.IniciarSesionUseCase;
import cl.primerplayer.identidad.application.port.out.HasherPassword;
import cl.primerplayer.identidad.application.port.out.UsuarioRepositorio;
import cl.primerplayer.identidad.domain.Usuario;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class IniciarSesionService implements IniciarSesionUseCase {

    private final UsuarioRepositorio usuarios;
    private final HasherPassword hasher;
    private final AperturaSesion apertura;
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
        // Cuenta creada con Google sin contraseña: se compara contra el señuelo (mismo costo) y siempre falla.
        boolean coincide = hasher.coincide(cmd.password(),
                usuario.tienePassword() ? usuario.passwordHash() : hasher.hashSenuelo()) && usuario.tienePassword();
        if (!coincide) {
            var bloqueo = props.bloqueo();
            Instant hasta = ahora.plus(bloqueo.duracion());
            if (usuarios.registrarIntentoFallido(usuario.id(), bloqueo.maxIntentos(), hasta, ahora)) {
                throw new CuentaBloqueadaException(hasta);
            }
            throw new CredencialesInvalidasException();
        }

        // El baneo (en AperturaSesion) se revisa después de la contraseña: no revela a un tercero que la cuenta existe.
        // TODO(pregunta-abierta): ¿se exige correo verificado para iniciar sesión? El contrato define
        // email-not-verified (403) pero no dice dónde aplica; por ahora se permite y se asume que lo exige checkout.
        // TODO: rate limiting por IP/correo con Bucket4j (rate-limited 429 + Retry-After).

        return apertura.abrir(usuario, cmd.ip(), cmd.userAgent(), ahora);
    }
}
