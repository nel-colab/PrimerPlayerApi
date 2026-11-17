package cl.primerplayer.catalogo.infrastructure.persistence;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Categorías: datos semilla (V6), de solo lectura para la aplicación. */
@Entity
@Immutable
@Table(name = "categoria")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class CategoriaEntity {

    @Id
    private Short id;

    private String slug;

    private String titulo;

    @Column(name = "parent_id")
    private Short parentId;

    @Column(name = "es_hoja")
    private boolean esHoja;

    private short orden;
}
