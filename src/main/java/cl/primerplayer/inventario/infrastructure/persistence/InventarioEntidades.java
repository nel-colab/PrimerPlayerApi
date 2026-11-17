package cl.primerplayer.inventario.infrastructure.persistence;

import java.time.Instant;

import org.hibernate.annotations.Immutable;

import cl.primerplayer.inventario.api.TipoMovimiento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Contenedor de las entidades JPA de inventario (todas package-private). */
final class InventarioEntidades {
    private InventarioEntidades() {}
}

/**
 * Vista mínima de {@code producto} para inventario. {@code @Immutable}: Hibernate nunca la escribe; el stock se
 * cambia solo con los UPDATE atómicos de {@link StockJpaRepository}.
 */
@Entity
@Immutable
@Table(name = "producto")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ProductoStockEntity {

    @Id
    private Long id;

    private int stock;

    @Column(name = "eliminado_en")
    private Instant eliminadoEn;
}

@Entity
@Table(name = "movimiento_stock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
class MovimientoStockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "producto_id", nullable = false)
    private long productoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimiento tipo;

    @Column(nullable = false)
    private int cantidad;

    @Column(name = "stock_resultante", nullable = false)
    private int stockResultante;

    @Column(length = 40)
    private String referencia;

    @Column(length = 200)
    private String motivo;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;
}

@Entity
@Table(name = "config_inventario")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ConfigInventarioEntity {

    static final short ID_UNICO = 1;

    @Id
    private Short id;

    @Column(name = "umbral_stock_bajo", nullable = false)
    private int umbralStockBajo;
}
