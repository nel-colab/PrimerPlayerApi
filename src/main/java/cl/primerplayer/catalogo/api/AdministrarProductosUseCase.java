package cl.primerplayer.catalogo.api;

import cl.primerplayer.shared.api.ConflictoVersionException;
import cl.primerplayer.shared.api.LimiteSoloAdminException;
import cl.primerplayer.shared.api.Rol;

/** Puerto de entrada: comandos del mantenedor de productos (VENDEDOR o ADMIN). */
public interface AdministrarProductosUseCase {

    /**
     * Crea el producto con stock 0 (el stock inicial se carga con un ajuste de inventario).
     *
     * @throws CodigoBarrasEnUsoException si el código de barras ya existe
     */
    ProductoAdmin crear(DatosProducto datos);

    /**
     * Reemplaza los datos editables.
     *
     * @param versionEsperada valor de {@code If-Match}
     * @param rol             rol de quien edita (el límite por cliente ya configurado solo lo cambia ADMIN)
     * @throws ConflictoVersionException       si el producto cambió desde que se leyó
     * @throws CodigoBarrasInmutableException  si un VENDEDOR agrega o cambia el código de barras (solo ADMIN)
     * @throws LimiteSoloAdminException        si un VENDEDOR cambia un límite ya configurado
     */
    ProductoAdmin actualizar(long id, int versionEsperada, DatosProducto datos, Rol rol);

    ProductoAdmin cambiarPublicacion(long id, boolean publicado);

    /** Borrado lógico ({@code eliminado_en}); el historial de pedidos no se rompe. */
    void eliminar(long id);
}
