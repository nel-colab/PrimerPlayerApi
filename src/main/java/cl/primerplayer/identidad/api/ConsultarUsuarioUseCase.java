package cl.primerplayer.identidad.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Puerto de entrada: lectura de usuarios (usado por {@code GET /me} y por historiales de otros módulos). */
public interface ConsultarUsuarioUseCase {

    /** Usuario activo y no eliminado con ese id. */
    Optional<UsuarioResumen> buscarActivo(long id);

    /**
     * Resumen de varios usuarios en una sola consulta, INCLUIDOS inactivos y eliminados: sirve para mostrar
     * historiales (pedidos, ventas) que nunca deben romperse. Los ids inexistentes no aparecen en el mapa.
     */
    Map<Long, UsuarioResumen> buscarPorIds(Collection<Long> ids);

    /**
     * Administradores a quienes se les puede escribir: rol ADMIN, activos, no eliminados, sin baneo vigente y con el
     * correo verificado (avisos de la tienda, por ejemplo productos esperando aprobación).
     */
    java.util.List<UsuarioResumen> administradoresActivos();

    /** {@code true} si el usuario existe, está activo, no está bloqueado y verificó su correo (requisito para comprar). */
    boolean puedeComprar(long id);

    /** Por qué un usuario puede o no comprar / solicitar (para responder con el código correcto). */
    default EstadoCompra estadoParaComprar(long id) {
        return puedeComprar(id) ? EstadoCompra.PERMITIDO : EstadoCompra.SIN_VERIFICAR;
    }

    /** {@code NO_DISPONIBLE}: inactiva o eliminada. {@code LISTA_NEGRA}: la tienda le impide comprar. */
    enum EstadoCompra { PERMITIDO, SIN_VERIFICAR, LISTA_NEGRA, NO_DISPONIBLE }

    /**
     * Id del cliente anónimo de mostrador ({@code mostrador@primerplayer.cl}, sembrado en V6): dueño de las ventas
     * del POS sin cliente registrado. No puede iniciar sesión.
     *
     * @throws IllegalStateException si no existe (la base no tiene los datos base de V6)
     */
    long clienteMostradorId();
}
