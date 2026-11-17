package cl.primerplayer.identidad.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface UsuarioJpaRepository extends JpaRepository<UsuarioEntity, Long> {

    // CAST a citext: con un parámetro varchar, PostgreSQL compararía como text (sensible a mayúsculas).
    @Query(value = "SELECT * FROM usuario WHERE email = CAST(:email AS citext)", nativeQuery = true)
    Optional<UsuarioEntity> buscarPorEmail(String email);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM usuario WHERE email = CAST(:email AS citext))", nativeQuery = true)
    boolean existeEmail(String email);

    /**
     * Incremento atómico (las expresiones del SET ven los valores anteriores de la fila).
     * Sin {@code @Modifying}: con RETURNING se ejecuta como consulta. Requiere transacción.
     */
    @Query(value = """
        UPDATE usuario SET
          intentos_fallidos = CASE WHEN intentos_fallidos + 1 >= :max THEN 0 ELSE intentos_fallidos + 1 END,
          bloqueado_hasta   = CASE WHEN intentos_fallidos + 1 >= :max THEN :hasta ELSE bloqueado_hasta END,
          actualizado_en    = :ahora
        WHERE id = :id
        RETURNING (bloqueado_hasta IS NOT NULL AND bloqueado_hasta > :ahora)
        """, nativeQuery = true)
    boolean registrarIntentoFallido(long id, int max, Instant hasta, Instant ahora);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL, actualizado_en = :ahora
        WHERE id = :id AND (intentos_fallidos > 0 OR bloqueado_hasta IS NOT NULL)
        """, nativeQuery = true)
    int reiniciarIntentos(long id, Instant ahora);
}
