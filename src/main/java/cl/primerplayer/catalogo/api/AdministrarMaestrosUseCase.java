package cl.primerplayer.catalogo.api;

import java.util.List;

/**
 * Puerto de entrada (solo ADMIN): mantenedor de categorías y franquicias. Una categoría o franquicia que alguna vez se
 * asoció a un producto (aunque el producto esté eliminado o el cambio haya quedado en una revisión) no se puede
 * eliminar: el historial y los pedidos siguen apuntando a ella.
 */
public interface AdministrarMaestrosUseCase {

    /** Categorías asignables (sin la agrupadora {@code todos}), por {@code orden}. */
    List<CategoriaAdmin> categorias();

    /** @param slug opcional: si viene vacío se genera desde el título. Es inmutable después de crear. */
    CategoriaAdmin crearCategoria(long actorId, String titulo, String slug, Integer orden);

    CategoriaAdmin actualizarCategoria(long actorId, int id, String titulo, Integer orden);

    void eliminarCategoria(long actorId, int id);

    /** Franquicias en orden alfabético. */
    List<FranquiciaAdmin> franquicias();

    FranquiciaAdmin crearFranquicia(long actorId, String nombre);

    /** Renombra la franquicia (los productos la referencian por id; las revisiones pendientes se actualizan). */
    FranquiciaAdmin renombrarFranquicia(long actorId, int id, String nombre);

    void eliminarFranquicia(long actorId, int id);

    /**
     * @param productos productos que la usan hoy (incluidos los eliminados)
     * @param enUso     {@code true} si alguna vez se asoció a un producto: no se puede eliminar
     */
    record CategoriaAdmin(int id, String slug, String titulo, int orden, long productos, boolean enUso) {}

    record FranquiciaAdmin(int id, String nombre, long productos, boolean enUso) {}
}
