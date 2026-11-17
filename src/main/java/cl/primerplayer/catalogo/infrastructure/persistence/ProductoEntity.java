package cl.primerplayer.catalogo.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Fila de {@code producto}. Catálogo lee {@code stock} pero nunca lo escribe (insertable/updatable = false). */
@Entity
@Table(name = "producto")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ProductoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nombre;

    private String descripcion;

    @Column(nullable = false)
    private long precio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id")
    private CategoriaEntity categoria;

    @Column(length = 40)
    private String tipo;

    @Column(length = 30)
    private String idioma;

    @Column(length = 60)
    private String marca;

    @Column(length = 30)
    private String plataforma;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "franquicia_id")
    private FranquiciaEntity franquicia;

    @Column(length = 80)
    private String linea;

    @Column(name = "codigo_barras", length = 14)
    private String codigoBarras;

    @Column(name = "limite_por_cliente")
    private Integer limitePorCliente;

    /** Propiedad de inventario: solo lectura desde catálogo. */
    @Column(insertable = false, updatable = false)
    private int stock;

    @Column(insertable = false, updatable = false)
    private int popularidad;

    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDate fechaIngreso;

    @Column(nullable = false)
    private boolean publicado;

    @Version
    private int version;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @Column(name = "eliminado_en")
    private Instant eliminadoEn;
}
