package cl.primerplayer.catalogo.infrastructure.persistence;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Fila de {@code producto_imagen}. Sin relación JPA con el producto a propósito: las imágenes se reemplazan
 * borrando e insertando (con @OneToMany Hibernate inserta antes de borrar y choca con la PK y la portada única).
 */
@Entity
@Table(name = "producto_imagen")
@IdClass(ProductoImagenEntity.Clave.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ProductoImagenEntity {

    @Id
    @Column(name = "producto_id")
    private long productoId;

    @Id
    private short posicion;

    @Column(name = "archivo_id")
    private UUID archivoId;

    @Column(name = "url_externa", length = 500)
    private String urlExterna;

    @Column(name = "es_portada", nullable = false)
    private boolean esPortada;

    ProductoImagenEntity(long productoId, short posicion, String urlExterna, boolean esPortada) {
        this(productoId, posicion, null, urlExterna, esPortada);
    }

    /** Exactamente uno de {@code archivoId} o {@code urlExterna} (CHECK de la base). */
    ProductoImagenEntity(long productoId, short posicion, UUID archivoId, String urlExterna, boolean esPortada) {
        this.productoId = productoId;
        this.posicion = posicion;
        this.archivoId = archivoId;
        this.urlExterna = archivoId == null ? urlExterna : null;
        this.esPortada = esPortada;
    }

    @lombok.EqualsAndHashCode
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    static class Clave implements Serializable {
        private long productoId;
        private short posicion;
    }
}
