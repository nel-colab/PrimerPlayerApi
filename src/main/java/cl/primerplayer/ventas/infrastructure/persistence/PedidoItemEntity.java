package cl.primerplayer.ventas.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Fila de {@code pedido_item}: snapshot de nombre y precio. {@code subtotal} lo calcula la base (columna generada). */
@Entity
@Table(name = "pedido_item")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PedidoItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pedido_id", nullable = false)
    private long pedidoId;

    @Column(nullable = false, length = 7)
    private String tipo;

    @Column(name = "producto_id")
    private Long productoId;

    @Column(name = "evento_id")
    private Long eventoId;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(nullable = false)
    private int cantidad;

    @Column(name = "precio_unitario", nullable = false)
    private long precioUnitario;

    @Column(insertable = false, updatable = false)
    private long subtotal;
}
