package cl.primerplayer.identidad.api;

import java.time.Instant;
import java.util.Set;

import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.Rol;

/**
 * Puerto de entrada: administración de usuarios (solo ADMIN). Contrato del frontend: docs/api-admin-usuarios.md.
 * Cada acción queda en la auditoría. El cliente anónimo de mostrador no se lista ni se administra.
 *
 * <p>Errores comunes: 404 {@code item-unavailable} (no existe), 409 {@code self-action} (sobre uno mismo),
 * 409 {@code last-admin} (dejaría la tienda sin administradores activos).
 */
public interface AdminUsuariosUseCase {

    Pagina<UsuarioAdmin> buscar(Filtro filtro);

    Indicadores indicadores();

    UsuarioAdmin cambiarRol(long actorId, long usuarioId, Rol rol);

    /** @param hasta {@code null} = permanente. Revoca todas sus sesiones. */
    UsuarioAdmin banear(long actorId, long usuarioId, String motivo, Instant hasta);

    UsuarioAdmin quitarBaneo(long actorId, long usuarioId);

    UsuarioAdmin ponerEnListaNegra(long actorId, long usuarioId, String motivo);

    UsuarioAdmin quitarDeListaNegra(long actorId, long usuarioId);

    /** Envía el correo de restablecimiento y marca que el siguiente ingreso debe cambiar la contraseña. */
    UsuarioAdmin solicitarCambioPassword(long actorId, long usuarioId);

    /** @throws YaVerificadoException 409 {@code already-verified} */
    UsuarioAdmin reenviarVerificacion(long actorId, long usuarioId);

    UsuarioAdmin cerrarSesiones(long actorId, long usuarioId);

    enum Estado { ACTIVO, BANEADO, LISTA_NEGRA, SIN_VERIFICAR }

    /** @param q nombre (sin tildes), correo o id; {@code estados} se combinan con OR */
    record Filtro(String q, Set<Rol> roles, Set<Estado> estados, int page, int size) {
        public Filtro {
            roles = roles == null ? Set.of() : Set.copyOf(roles);
            estados = estados == null ? Set.of() : Set.copyOf(estados);
        }
    }

    record Indicadores(long total, long clientes, long internos, long baneados, long listaNegra, long sinVerificar) {}

    /** @param baneo {@code null} si no hay baneo vigente */
    record UsuarioAdmin(long id, String email, String nombre, Rol rol, String telefono, boolean emailVerificado,
                        Baneo baneo, ListaNegra listaNegra, boolean cambioPasswordPendiente, Instant creadoEn,
                        Instant ultimoAcceso) {}

    /** @param hasta {@code null} = permanente */
    record Baneo(String motivo, Instant hasta, Instant creadoEn) {}

    record ListaNegra(String motivo, Instant creadoEn) {}
}
