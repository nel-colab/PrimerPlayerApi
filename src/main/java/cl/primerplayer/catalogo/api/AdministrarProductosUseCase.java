package cl.primerplayer.catalogo.api;

import cl.primerplayer.shared.api.ConflictoVersionException;
import cl.primerplayer.shared.api.LimiteSoloAdminException;
import cl.primerplayer.shared.api.Rol;

/**
 * Puerto de entrada: comandos del mantenedor de productos (VENDEDOR o ADMIN).
 *
 * <p>Autorización previa: lo que crea un VENDEDOR nace {@link EstadoAprobacion#PENDIENTE} y sin publicar; lo que
 * edita sobre un producto ya aprobado queda como propuesta hasta que un ADMIN la apruebe
 * ({@link RevisionesProductoUseCase}). Lo que hace un ADMIN se aplica directo.
 */
public interface AdministrarProductosUseCase {

    /**
     * Crea el producto con stock 0 (el stock inicial se carga con un ajuste de inventario). Si lo crea un VENDEDOR
     * queda pendiente de aprobación y sin publicar, aunque {@code datos.publicado()} sea {@code true}.
     *
     * @throws CodigoBarrasEnUsoException si el código de barras ya existe
     */
    ProductoAdmin crear(DatosProducto datos, Actor actor);

    /**
     * Reemplaza los datos editables.
     *
     * <ul>
     *   <li>ADMIN: se aplica directo. Publicar un producto no aprobado lo aprueba.</li>
     *   <li>VENDEDOR sobre un producto no aprobado (pendiente o rechazado): se aplica directo, sigue sin publicar y
     *       vuelve a quedar pendiente de aprobación.</li>
     *   <li>VENDEDOR sobre un producto aprobado: no se aplica; queda como propuesta pendiente
     *       ({@link ResultadoCambio#enRevision()}). El producto sigue a la venta con lo aprobado.</li>
     * </ul>
     *
     * @param versionEsperada valor de {@code If-Match}
     * @throws ConflictoVersionException       si el producto cambió desde que se leyó
     * @throws CodigoBarrasInmutableException  si un VENDEDOR agrega o cambia el código de barras (solo ADMIN)
     * @throws LimiteSoloAdminException        si un VENDEDOR cambia un límite ya configurado
     */
    ResultadoCambio actualizar(long id, int versionEsperada, DatosProducto datos, Actor actor);

    /**
     * Ocultar se aplica siempre directo. Publicar: ADMIN directo (y aprueba el producto si no lo estaba);
     * VENDEDOR sobre un producto aprobado queda como propuesta pendiente.
     *
     * @throws ProductoNoAprobadoException si un VENDEDOR publica un producto que aún no está aprobado
     */
    ResultadoCambio cambiarPublicacion(long id, boolean publicado, Actor actor);

    /** Borrado lógico ({@code eliminado_en}); el historial de pedidos no se rompe. Descarta revisiones pendientes. */
    void eliminar(long id, Actor actor);

    /**
     * Código de barras interno para un producto que no trae uno de fábrica: EAN-13 {@code 200} + correlativo +
     * verificador, que no está en uso. No lo reserva: queda asignado al guardar el producto con él. Dos llamadas
     * nunca devuelven el mismo código.
     */
    String generarCodigoBarras();

    /** Quién ejecuta el comando (sale del token). */
    record Actor(long usuarioId, Rol rol) {

        public boolean esAdmin() {
            return rol == Rol.ADMIN;
        }
    }

    /**
     * @param producto   el producto como quedó (si {@code enRevision}, sin el cambio: sigue con lo aprobado)
     * @param enRevision el cambio quedó pendiente de aprobación de un ADMIN
     */
    record ResultadoCambio(ProductoAdmin producto, boolean enRevision) {}
}
