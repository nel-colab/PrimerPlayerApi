package cl.primerplayer.eventos.infrastructure.persistence;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Fila de {@code evento}. {@code cupos_ocupados} es de solo lectura aquí: lo mueven los UPDATE atómicos de cupos. */
@Entity
@Table(name = "evento")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class EventoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String titulo;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(name = "fecha_hora", nullable = false)
    private Instant fechaHora;

    @Column(name = "fecha_hora_fin")
    private Instant fechaHoraFin;

    @Column(nullable = false, length = 160)
    private String lugar;

    @Column(nullable = false)
    private long precio;

    @Column(name = "cupos_totales")
    private Integer cuposTotales;

    @Column(name = "cupos_ocupados", insertable = false, updatable = false)
    private int cuposOcupados;

    @Column(name = "limite_por_cliente")
    private Integer limitePorCliente;

    @Column(nullable = false)
    private String descripcion;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] consideraciones;

    @Column(name = "imagen_url", length = 500)
    private String imagenUrl;

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
