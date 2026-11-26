package cl.primerplayer.catalogo.application.port.out;

import java.util.List;
import java.util.Optional;

/** Puerto de salida: escritura de categorías y franquicias y cuánto se usan. */
public interface MaestrosAdminRepositorio {

    /** Categorías asignables (todas menos la agrupadora {@code todos}), por orden. */
    List<FilaCategoria> categorias();

    Optional<FilaCategoria> categoria(int id);

    /** ¿Otra categoría (distinta de {@code exceptoId}) usa ese slug o ese título (sin distinguir mayúsculas)? */
    boolean existeCategoria(String slug, String titulo, Integer exceptoId);

    /** Orden para una categoría nueva: al final. */
    int siguienteOrden();

    int crearCategoria(String slug, String titulo, int orden);

    void actualizarCategoria(int id, String titulo, int orden);

    /** @throws org.springframework.dao.DataIntegrityViolationException si un producto la referencia (FK) */
    void eliminarCategoria(int id);

    List<FilaFranquicia> franquicias();

    Optional<FilaFranquicia> franquicia(int id);

    boolean existeFranquicia(String nombre, Integer exceptoId);

    int crearFranquicia(String nombre);

    void renombrarFranquicia(int id, String nombre);

    /** Las revisiones pendientes guardan la franquicia por nombre: se actualizan para que se puedan aprobar. */
    int renombrarFranquiciaEnRevisionesPendientes(String anterior, String nuevo);

    /** @throws org.springframework.dao.DataIntegrityViolationException si un producto la referencia (FK) */
    void eliminarFranquicia(int id);

    /**
     * @param productos  productos que la referencian (incluidos los eliminados)
     * @param revisiones revisiones de producto (propuestas o aprobadas) que la mencionan
     */
    record FilaCategoria(int id, String slug, String titulo, int orden, long productos, long revisiones) {
        public boolean enUso() {
            return productos > 0 || revisiones > 0;
        }
    }

    record FilaFranquicia(int id, String nombre, long productos, long revisiones) {
        public boolean enUso() {
            return productos > 0 || revisiones > 0;
        }
    }
}
