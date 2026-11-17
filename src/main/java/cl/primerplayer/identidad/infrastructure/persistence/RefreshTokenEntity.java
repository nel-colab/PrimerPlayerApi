package cl.primerplayer.identidad.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.ColumnTransformer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "refresh_token")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class RefreshTokenEntity {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private long usuarioId;

    @Column(nullable = false)
    private UUID familia;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "revocado_en")
    private Instant revocadoEn;

    @Column(name = "reemplazado_por")
    private UUID reemplazadoPor;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @ColumnTransformer(read = "host(ip)", write = "CAST(? AS inet)")
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;
}
