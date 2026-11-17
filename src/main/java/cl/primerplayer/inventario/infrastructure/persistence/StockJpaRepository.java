package cl.primerplayer.inventario.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * UPDATE atómicos sobre {@code producto.stock}. Sin {@code @Modifying}: con RETURNING se ejecutan como consulta y
 * devuelven el stock resultante; vacío = 0 filas afectadas. Deben llamarse dentro de una transacción.
 * El {@code CHECK (stock >= 0)} de la base es la red final.
 */
interface StockJpaRepository extends Repository<ProductoStockEntity, Long> {

    @Query(value = """
        UPDATE producto SET stock = stock - :cantidad, actualizado_en = now()
        WHERE id = :id AND eliminado_en IS NULL AND stock >= :cantidad
        RETURNING stock
        """, nativeQuery = true)
    Optional<Integer> descontar(long id, int cantidad);

    @Query(value = """
        UPDATE producto SET stock = stock + :delta, actualizado_en = now()
        WHERE id = :id AND eliminado_en IS NULL AND stock + :delta >= 0
        RETURNING stock
        """, nativeQuery = true)
    Optional<Integer> ajustar(long id, int delta);

    @Query(value = """
        UPDATE producto SET stock = stock + :cantidad, actualizado_en = now()
        WHERE id = :id
        RETURNING stock
        """, nativeQuery = true)
    Optional<Integer> reponer(long id, int cantidad);

    @Query(value = "SELECT stock FROM producto WHERE id = :id AND eliminado_en IS NULL", nativeQuery = true)
    Optional<Integer> stockActivo(long id);

    boolean existsById(Long id);
}
