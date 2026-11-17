package cl.primerplayer.ventas.infrastructure.persistence;

import java.time.Instant;

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

/** Fila de {@code pedido}. Los ids de cliente y vendedor son referencias a identidad (sin relación JPA). */
@Entity
@Table(name = "pedido")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PedidoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String codigo;

    @Column(name = "cliente_id", nullable = false)
    private long clienteId;

    @Column(name = "vendedor_id")
    private Long vendedorId;

    @Column(nullable = false, length = 6)
    private String canal;

    @Column(nullable = false, length = 16)
    private String estado;

    @Column(nullable = false)
    private long total;

    @Column(name = "pagado_en")
    private Instant pagadoEn;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;
}
