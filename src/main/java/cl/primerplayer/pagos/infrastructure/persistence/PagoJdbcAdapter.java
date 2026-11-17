package cl.primerplayer.pagos.infrastructure.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import cl.primerplayer.pagos.api.ConsultarPagosUseCase.PagoVista;
import cl.primerplayer.pagos.api.PagosMostrador.PagoMostrador;
import cl.primerplayer.pagos.application.port.out.PagoRepositorio;
import lombok.RequiredArgsConstructor;

/** Adaptador JDBC de {@code pago}. Los CHECK de la base por método son la red final de las validaciones. */
@Component
@RequiredArgsConstructor
class PagoJdbcAdapter implements PagoRepositorio {

    private final JdbcTemplate jdbc;

    @Override
    public void insertarMostrador(long pedidoId, PagoMostrador p, Long vuelto, long saldoAFavor, Instant ahora) {
        boolean efectivo = "efectivo".equals(p.metodo());
        boolean carta = "cartas-tcg".equals(p.metodo());
        boolean boleta = "debito".equals(p.metodo()) || "credito".equals(p.metodo()) || "prepago".equals(p.metodo());
        try {
            jdbc.update("""
                    INSERT INTO pago (pedido_id, metodo, monto, numero_boleta, comprobante_archivo_id, carta_nombre,
                                      carta_codigo, carta_valor, saldo_a_favor, efectivo_recibido, vuelto_entregado, creado_en)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, pedidoId, p.metodo(), p.monto(),
                    boleta ? p.numeroBoleta().strip() : null,
                    "transferencia".equals(p.metodo()) ? p.comprobanteId() : null,
                    carta ? p.cartaNombre().strip() : null, carta ? p.cartaCodigo().strip() : null,
                    carta ? p.cartaValor() : null, saldoAFavor,
                    efectivo ? p.efectivoRecibido() : null, efectivo ? vuelto : null,
                    ahora.atOffset(ZoneOffset.UTC));
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("comprobante_archivo_id")) {
                throw new ComprobanteInexistenteException();
            }
            throw e;
        }
    }

    @Override
    public List<PagoFila> dePedidos(Collection<Long> pedidoIds) {
        // Los pagos webpay se completan con su transacción autorizada (no hay número de tarjeta completo en ninguna tabla).
        return jdbc.query("""
                SELECT p.pedido_id, p.metodo, p.monto, p.numero_boleta, p.comprobante_archivo_id, p.carta_nombre,
                       p.carta_codigo, p.carta_valor, p.saldo_a_favor, p.efectivo_recibido, p.vuelto_entregado, p.creado_en,
                       t.authorization_code, t.card_last4, t.payment_type_code, t.installments_number
                FROM pago p
                LEFT JOIN transaccion_webpay t
                       ON p.metodo = 'webpay' AND t.pedido_id = p.pedido_id AND t.estado IN ('AUTORIZADA', 'REVERSADA', 'ANULADA')
                WHERE p.pedido_id = ANY (?)
                ORDER BY p.pedido_id, p.id
                """, (rs, n) -> new PagoFila(rs.getLong("pedido_id"), new PagoVista(
                        rs.getString("metodo"), rs.getLong("monto"), rs.getString("numero_boleta"),
                        rs.getObject("comprobante_archivo_id", UUID.class), rs.getString("carta_nombre"),
                        rs.getString("carta_codigo"), (Long) rs.getObject("carta_valor"), rs.getLong("saldo_a_favor"),
                        (Long) rs.getObject("efectivo_recibido"), (Long) rs.getObject("vuelto_entregado"),
                        rs.getString("authorization_code"), rs.getString("card_last4"), rs.getString("payment_type_code"),
                        cuotas(rs.getObject("installments_number")),
                        rs.getObject("creado_en", OffsetDateTime.class).toInstant())),
                (Object) pedidoIds.toArray(Long[]::new));
    }

    private static Integer cuotas(Object valor) {
        return valor == null ? null : ((Number) valor).intValue();
    }
}
