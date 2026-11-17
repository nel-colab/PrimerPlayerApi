package cl.primerplayer.pagos.infrastructure.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import cl.primerplayer.pagos.api.PasarelaWebpay.ResultadoWebpay;
import cl.primerplayer.pagos.api.TransaccionesWebpay.EstadoTransaccion;
import cl.primerplayer.pagos.api.TransaccionesWebpay.TransaccionWebpay;
import cl.primerplayer.pagos.application.port.out.TransaccionWebpayRepositorio;
import lombok.RequiredArgsConstructor;

/** Adaptador JDBC de {@code transaccion_webpay} y {@code pago}. */
@Component
@RequiredArgsConstructor
class TransaccionWebpayJdbcAdapter implements TransaccionWebpayRepositorio {

    private static final String COLUMNAS = "SELECT id, pedido_id, buy_order, token, monto, estado FROM transaccion_webpay ";

    private static final RowMapper<TransaccionWebpay> FILA = (rs, n) -> new TransaccionWebpay(rs.getLong("id"),
            rs.getLong("pedido_id"), rs.getString("buy_order"), rs.getString("token"), rs.getLong("monto"),
            EstadoTransaccion.valueOf(rs.getString("estado")));

    private final JdbcTemplate jdbc;

    @Override
    public void insertarCreada(long pedidoId, String buyOrder, String sessionId, String token, long monto, Instant ahora) {
        jdbc.update("""
                INSERT INTO transaccion_webpay (pedido_id, buy_order, session_id, token, monto, estado, creado_en)
                VALUES (?, ?, ?, ?, ?, 'CREADA', ?)
                """, pedidoId, buyOrder, sessionId, token, monto, utc(ahora));
    }

    @Override
    public Optional<TransaccionWebpay> porToken(String token, boolean bloquear) {
        return jdbc.query(COLUMNAS + "WHERE token = ?" + (bloquear ? " FOR UPDATE" : ""), FILA, token)
                .stream().findFirst();
    }

    @Override
    public Optional<TransaccionWebpay> porBuyOrder(String buyOrder, boolean bloquear) {
        return jdbc.query(COLUMNAS + "WHERE buy_order = ?" + (bloquear ? " FOR UPDATE" : ""), FILA, buyOrder)
                .stream().findFirst();
    }

    @Override
    public Optional<TransaccionWebpay> creadaDelPedido(long pedidoId) {
        return jdbc.query(COLUMNAS + "WHERE pedido_id = ? AND estado = 'CREADA' ORDER BY id DESC LIMIT 1", FILA, pedidoId)
                .stream().findFirst();
    }

    @Override
    public TransaccionWebpay porId(long id) {
        return jdbc.query(COLUMNAS + "WHERE id = ?", FILA, id).stream().findFirst()
                .orElseThrow(() -> new NoSuchElementException("transaccion_webpay " + id));
    }

    @Override
    public void guardarResultado(long id, EstadoTransaccion estado, ResultadoWebpay r, Instant ahora) {
        jdbc.update("""
                UPDATE transaccion_webpay
                SET estado = ?, response_code = ?, authorization_code = ?, payment_type_code = ?, installments_number = ?,
                    card_last4 = ?, accounting_date = ?, transaction_date = ?, respuesta_commit = CAST(? AS jsonb),
                    confirmado_en = ?
                WHERE id = ?
                """, estado.name(), r.responseCode(), r.authorizationCode(), r.paymentTypeCode(), r.cuotas(),
                r.cardLast4(), r.accountingDate(), r.transactionDate() == null ? null : utc(r.transactionDate()),
                r.json(), utc(ahora), id);
    }

    @Override
    public void cerrar(long id, EstadoTransaccion estado, Instant ahora) {
        jdbc.update("UPDATE transaccion_webpay SET estado = ?, confirmado_en = ? WHERE id = ?",
                estado.name(), utc(ahora), id);
    }

    @Override
    public void insertarPagoWebpay(long pedidoId, long monto, Instant ahora) {
        jdbc.update("INSERT INTO pago (pedido_id, metodo, monto, creado_en) VALUES (?, 'webpay', ?, ?)",
                pedidoId, monto, utc(ahora));
    }

    /** UTC explícito: no depende de la zona horaria de la JVM ni de la sesión. */
    private static OffsetDateTime utc(Instant i) {
        return i.atOffset(ZoneOffset.UTC);
    }
}
