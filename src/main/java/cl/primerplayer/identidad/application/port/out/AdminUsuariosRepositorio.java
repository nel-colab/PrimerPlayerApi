package cl.primerplayer.identidad.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import cl.primerplayer.identidad.api.AdminUsuariosUseCase.Filtro;
import cl.primerplayer.identidad.api.AdminUsuariosUseCase.Indicadores;
import cl.primerplayer.identidad.api.AdminUsuariosUseCase.UsuarioAdmin;
import cl.primerplayer.shared.api.Rol;

/** Puerto de salida: lecturas y cambios del panel de usuarios. Excluye el cliente anónimo y los eliminados. */
public interface AdminUsuariosRepositorio {

    List<UsuarioAdmin> buscar(Filtro filtro, Instant ahora);

    long contar(Filtro filtro, Instant ahora);

    Indicadores indicadores(Instant ahora);

    /** Bloquea la fila ({@code FOR UPDATE}): dos administradores no se pisan al cambiar la misma cuenta. */
    Optional<UsuarioAdmin> bloquear(long usuarioId, Instant ahora);

    /** Administradores activos, no eliminados y sin baneo vigente. */
    long adminsActivos(Instant ahora);

    void cambiarRol(long usuarioId, Rol rol, Instant ahora);

    void banear(long usuarioId, String motivo, Instant hasta, Instant ahora);

    void quitarBaneo(long usuarioId, Instant ahora);

    void ponerEnListaNegra(long usuarioId, String motivo, Instant ahora);

    void quitarDeListaNegra(long usuarioId, Instant ahora);

    void marcarCambioPassword(long usuarioId, Instant ahora);

    /** Bloquea la tabla de administradores mientras se decide si queda alguno (evita que dos admins se quiten a la vez). */
    void bloquearAdmins();
}
