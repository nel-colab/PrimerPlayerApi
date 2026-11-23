package cl.primerplayer.identidad.application.port.out;

import java.time.Instant;
import java.util.Optional;

import cl.primerplayer.identidad.domain.Usuario;

/** Puerto de salida: persistencia de usuarios. */
public interface UsuarioRepositorio {

    /** Búsqueda insensible a mayúsculas (columna CITEXT). */
    Optional<Usuario> buscarPorEmail(String email);

    Optional<Usuario> buscarPorId(long id);

    /** Incluye inactivos y eliminados (para historiales). */
    java.util.List<Usuario> buscarPorIds(java.util.Collection<Long> ids);

    boolean existeEmail(String email);

    /**
     * Inserta y devuelve el usuario con su id.
     *
     * @throws cl.primerplayer.identidad.api.EmailEnUsoException si choca con {@code uq_usuario_email}
     */
    Usuario guardarNuevo(Usuario usuario);

    /**
     * Suma un intento fallido de forma atómica (UPDATE condicional, sin leer-restar-guardar en Java).
     * Al llegar a {@code maxIntentos} bloquea hasta {@code bloquearHasta} y reinicia el contador.
     *
     * @return {@code true} si este intento dejó la cuenta bloqueada
     */
    boolean registrarIntentoFallido(long usuarioId, int maxIntentos, Instant bloquearHasta, Instant ahora);

    /** Reinicia los intentos fallidos y registra el último acceso. */
    void registrarInicioExitoso(long usuarioId, Instant ahora);

    /** Baneo vigente (sin fecha de término o con término futuro). */
    Optional<Baneo> baneoVigente(long usuarioId, Instant ahora);

    boolean enListaNegra(long usuarioId);

    /** Usuario vinculado a esa cuenta de Google (no eliminado). */
    Optional<Usuario> buscarPorGoogleSub(String googleSub);

    /** {@code sub} de Google vinculado al usuario, si tiene. */
    Optional<String> googleSubDe(long usuarioId);

    /**
     * Vincula la cuenta de Google y marca el correo como verificado (si no lo estaba).
     *
     * @throws cl.primerplayer.identidad.api.GoogleCuentaEnUsoException si ese {@code sub} ya está en otro usuario
     */
    void vincularGoogle(long usuarioId, String googleSub, Instant ahora);

    /** @param hasta {@code null} = permanente */
    record Baneo(String motivo, Instant hasta) {}
}
