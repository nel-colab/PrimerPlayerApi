package cl.primerplayer.catalogo.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/** Repositorios Spring Data del catálogo (package-private: solo los usan los adaptadores). */
final class Repositorios {
    private Repositorios() {}
}

interface ProductoJpaRepository extends JpaRepository<ProductoEntity, Long>, JpaSpecificationExecutor<ProductoEntity> {

    Optional<ProductoEntity> findByIdAndEliminadoEnIsNull(long id);

    Optional<ProductoEntity> findByCodigoBarrasAndEliminadoEnIsNull(String codigoBarras);

    Optional<ProductoEntity> findFirstByNombreIgnoreCaseAndEliminadoEnIsNullOrderByIdAsc(String nombre);

    List<ProductoEntity> findAllByEliminadoEnIsNullOrderByIdAsc();

    @Query(value = """
        SELECT count(*)                                                   AS total,
               count(*) FILTER (WHERE stock > 0 AND stock <= :umbral)      AS stockBajo,
               count(*) FILTER (WHERE stock = 0)                           AS agotados
        FROM producto WHERE eliminado_en IS NULL
        """, nativeQuery = true)
    IndicadoresFila indicadores(int umbral);

    /**
     * Relacionados de un producto: misma categoría primero, luego misma franquicia; con stock antes que agotados.
     * Solo publicados y no eliminados; nunca el propio producto.
     */
    @Query(value = """
        SELECT p.* FROM producto p
        JOIN producto base ON base.id = :id
        WHERE p.id <> base.id AND p.publicado AND p.eliminado_en IS NULL
          AND (p.categoria_id = base.categoria_id
               OR (base.franquicia_id IS NOT NULL AND p.franquicia_id = base.franquicia_id))
        ORDER BY (p.categoria_id = base.categoria_id) DESC, (p.stock > 0) DESC, p.popularidad DESC, p.id DESC
        LIMIT :limite
        """, nativeQuery = true)
    List<ProductoEntity> relacionados(long id, int limite);

    interface IndicadoresFila {
        long getTotal();
        long getStockBajo();
        long getAgotados();
    }
}

interface ProductoImagenJpaRepository extends JpaRepository<ProductoImagenEntity, ProductoImagenEntity.Clave> {

    List<ProductoImagenEntity> findByProductoIdOrderByPosicion(long productoId);

    List<ProductoImagenEntity> findByProductoIdIn(Collection<Long> productoIds);
}

interface CategoriaJpaRepository extends JpaRepository<CategoriaEntity, Short> {

    Optional<CategoriaEntity> findBySlug(String slug);
}

interface FranquiciaJpaRepository extends JpaRepository<FranquiciaEntity, Short> {

    Optional<FranquiciaEntity> findByNombreIgnoreCase(String nombre);
}
