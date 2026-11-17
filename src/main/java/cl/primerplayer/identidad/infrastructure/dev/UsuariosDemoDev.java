package cl.primerplayer.identidad.infrastructure.dev;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.identidad.application.port.out.HasherPassword;
import cl.primerplayer.identidad.application.port.out.UsuarioRepositorio;
import cl.primerplayer.identidad.domain.Usuario;
import cl.primerplayer.shared.api.Rol;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Solo perfil {@code dev}: crea admin, vendedor y cliente de prueba si no existen. El hash se genera con el mismo
 * {@link HasherPassword} de la aplicación (por eso no va en el SQL de semilla).
 * <p>Desactivado por defecto; para volver a crearlos arrancar con {@code pp.dev.usuarios.crear=true}.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "pp.dev.usuarios.crear", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
class UsuariosDemoDev implements ApplicationRunner {

    private final UsuarioRepositorio usuarios;
    private final HasherPassword hasher;
    private final Clock clock;

    @Value("${pp.dev.usuarios.password:PrimerPlayer2026!}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        crear("Admin Demo", "admin@primerplayer.cl", Rol.ADMIN);
        crear("Vendedor Demo", "vendedor@primerplayer.cl", Rol.VENDEDOR);
        crear("Cliente Demo", "cliente@primerplayer.cl", Rol.CLIENTE);
    }

    private void crear(String nombre, String email, Rol rol) {
        if (usuarios.existeEmail(email)) {
            return;
        }
        usuarios.guardarNuevo(Usuario.crearVerificado(nombre, email, hasher.hashear(password), rol, clock.instant()));
        log.info("Usuario demo creado: {} ({})", email, rol);   // sin contraseña en el log
    }
}
