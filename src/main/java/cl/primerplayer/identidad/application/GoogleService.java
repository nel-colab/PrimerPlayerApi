package cl.primerplayer.identidad.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import cl.primerplayer.identidad.api.CuentaUseCase;
import cl.primerplayer.identidad.api.CuentaUseCase.Perfil;
import cl.primerplayer.identidad.api.GoogleCorreoDistintoException;
import cl.primerplayer.identidad.api.GoogleCuentaEnUsoException;
import cl.primerplayer.identidad.api.GoogleTokenInvalidoException;
import cl.primerplayer.identidad.api.GoogleUseCase;
import cl.primerplayer.identidad.api.IniciarSesionUseCase.SesionIniciada;
import cl.primerplayer.identidad.application.port.out.UsuarioRepositorio;
import cl.primerplayer.identidad.application.port.out.VerificadorGoogle;
import cl.primerplayer.identidad.application.port.out.VerificadorGoogle.IdentidadGoogle;
import cl.primerplayer.identidad.domain.Usuario;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;

/**
 * Inicio de sesión y verificación de correo con Google. La validación del ID token puede ir a buscar las llaves de
 * Google por HTTP, así que ocurre <b>antes</b> de abrir la transacción (regla 4); el resto va en un
 * {@link TransactionTemplate}.
 */
@Service
@RequiredArgsConstructor
class GoogleService implements GoogleUseCase {

    private final VerificadorGoogle verificador;
    private final UsuarioRepositorio usuarios;
    private final AperturaSesion apertura;
    private final CuentaUseCase cuenta;
    private final TransactionTemplate tx;
    private final Clock clock;

    @Override
    public Optional<String> clientId() {
        return verificador.clientId();
    }

    @Override
    public SesionIniciada iniciarSesion(String credential, String ip, String userAgent) {
        IdentidadGoogle g = confirmada(credential);
        return tx.execute(estado -> {
            Instant ahora = clock.instant();
            String email = Usuario.normalizarEmail(g.email());
            Usuario usuario = usuarios.buscarPorGoogleSub(g.sub())
                    .or(() -> vincularPorCorreo(email, g.sub(), ahora))
                    .orElseGet(() -> crear(g, email, ahora));
            if (!usuario.puedeIniciarSesion()) {
                throw new GoogleTokenInvalidoException();   // desactivada o eliminada: mismo error genérico
            }
            return apertura.abrir(usuario, ip, userAgent, ahora);
        });
    }

    @Override
    public Perfil verificarCorreo(long usuarioId, String credential) {
        IdentidadGoogle g = confirmada(credential);
        tx.executeWithoutResult(estado -> {
            Usuario u = usuarios.buscarPorId(usuarioId).filter(Usuario::puedeIniciarSesion)
                    .orElseThrow(() -> new RecursoNoEncontradoException("La cuenta no existe."));
            if (!u.email().equals(Usuario.normalizarEmail(g.email()))) {
                throw new GoogleCorreoDistintoException();
            }
            Optional<String> vinculado = usuarios.googleSubDe(usuarioId);
            if (vinculado.isPresent() && !vinculado.get().equals(g.sub())) {
                throw new GoogleCuentaEnUsoException();
            }
            usuarios.buscarPorGoogleSub(g.sub()).filter(otro -> !otro.id().equals(usuarioId)).ifPresent(otro -> {
                throw new GoogleCuentaEnUsoException();
            });
            usuarios.vincularGoogle(usuarioId, g.sub(), clock.instant());
        });
        return cuenta.perfil(usuarioId);
    }

    /** Valida el token (fuera de transacción) y exige que Google confirme el correo. */
    private IdentidadGoogle confirmada(String credential) {
        IdentidadGoogle g = verificador.verificar(credential);
        if (!g.emailVerificado()) {
            throw new GoogleTokenInvalidoException();
        }
        return g;
    }

    /**
     * El correo ya tiene cuenta (con contraseña o creada por la tienda): Google prueba que es del dueño, así que se
     * vincula y queda verificado. Si ya está vinculada a otra cuenta de Google, no se toca.
     */
    private Optional<Usuario> vincularPorCorreo(String email, String sub, Instant ahora) {
        return usuarios.buscarPorEmail(email).map(u -> {
            if (usuarios.googleSubDe(u.id()).isPresent()) {
                throw new GoogleCuentaEnUsoException();
            }
            usuarios.vincularGoogle(u.id(), sub, ahora);
            return usuarios.buscarPorId(u.id()).orElseThrow();
        });
    }

    private Usuario crear(IdentidadGoogle g, String email, Instant ahora) {
        Usuario nuevo = usuarios.guardarNuevo(Usuario.registrarConGoogle(g.nombre(), email, ahora));
        usuarios.vincularGoogle(nuevo.id(), g.sub(), ahora);
        return nuevo;
    }
}
