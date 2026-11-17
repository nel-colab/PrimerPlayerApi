package cl.primerplayer.identidad.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.identidad.api.EmailEnUsoException;
import cl.primerplayer.identidad.api.RegistrarUsuarioUseCase;
import cl.primerplayer.identidad.api.UsuarioResumen;
import cl.primerplayer.identidad.application.port.out.HasherPassword;
import cl.primerplayer.identidad.application.port.out.TokenUnUsoRepositorio;
import cl.primerplayer.identidad.application.port.out.UsuarioRepositorio;
import cl.primerplayer.identidad.domain.TokenOpaco;
import cl.primerplayer.identidad.domain.TokenUnUso;
import cl.primerplayer.identidad.domain.Usuario;
import cl.primerplayer.identidad.events.UsuarioRegistrado;
import cl.primerplayer.shared.api.ValidacionException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class RegistrarUsuarioService implements RegistrarUsuarioUseCase {

    /** BCrypt solo considera los primeros 72 bytes: más largo sería una contraseña truncada en silencio. */
    static final int MAX_BYTES_PASSWORD = 72;

    private final UsuarioRepositorio usuarios;
    private final TokenUnUsoRepositorio tokens;
    private final HasherPassword hasher;
    private final ApplicationEventPublisher eventos;
    private final IdentidadProperties props;
    private final Clock clock;

    @Override
    @Transactional
    public UsuarioResumen registrar(RegistrarUsuarioCommand cmd) {
        if (cmd.password().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_PASSWORD) {
            throw new ValidacionException("password", "La contraseña es demasiado larga.");
        }
        String email = Usuario.normalizarEmail(cmd.email());
        // TODO(pregunta-abierta): responder 409 email-taken permite enumerar correos registrados.
        // Alternativa: responder siempre 201/202 y avisar por correo al dueño de la cuenta existente.
        if (usuarios.existeEmail(email)) {
            throw new EmailEnUsoException();
        }

        Instant ahora = clock.instant();
        Usuario usuario = usuarios.guardarNuevo(
                Usuario.registrarCliente(cmd.nombre(), email, hasher.hashear(cmd.password()), ahora));

        TokenOpaco verificacion = TokenOpaco.generar();
        tokens.guardar(TokenUnUso.verificacion(usuario.id(), verificacion, ahora, props.verificacionTtl()));

        // notificaciones lo escucha después del commit (outbox de Spring Modulith) y envía el correo.
        eventos.publishEvent(new UsuarioRegistrado(usuario.id(), usuario.email(), usuario.nombre(),
                verificacion.valor()));

        return UsuarioResumenes.de(usuario);
    }
}
