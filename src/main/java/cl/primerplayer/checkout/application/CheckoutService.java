package cl.primerplayer.checkout.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import cl.primerplayer.checkout.api.CheckoutUseCase;
import cl.primerplayer.pagos.api.PasarelaNoDisponibleException;
import cl.primerplayer.pagos.api.PasarelaWebpay;
import cl.primerplayer.pagos.api.PasarelaWebpay.InicioWebpay;
import cl.primerplayer.pagos.api.PasarelaWebpay.ResultadoWebpay;
import cl.primerplayer.pagos.api.TransaccionesWebpay;
import cl.primerplayer.pagos.api.TransaccionesWebpay.EstadoTransaccion;
import cl.primerplayer.pagos.api.TransaccionesWebpay.TransaccionWebpay;
import cl.primerplayer.ventas.api.EstadoPedido;
import cl.primerplayer.ventas.api.VentaWebUseCase;
import cl.primerplayer.ventas.api.VentaWebUseCase.LineaCarrito;
import cl.primerplayer.ventas.api.VentaWebUseCase.PedidoCreado;
import cl.primerplayer.ventas.api.VentaWebUseCase.ValidacionCarrito;
import lombok.extern.slf4j.Slf4j;

/**
 * Orquestador. Regla de oro: cada bloque {@code tx.execute(...)} es una transacción corta de base de datos y las
 * llamadas a Webpay ocurren SIEMPRE entre bloques, nunca dentro (regla 4 del proyecto).
 */
@Service
@EnableConfigurationProperties(CheckoutProperties.class)
@Slf4j
class CheckoutService implements CheckoutUseCase {

    private static final String PROVEEDOR = "webpay";
    private static final int LOTE_VENCIDOS = 50;

    private final VentaWebUseCase ventas;
    private final PasarelaWebpay pasarela;
    private final TransaccionesWebpay transacciones;
    private final CheckoutProperties props;
    private final TransactionTemplate tx;

    CheckoutService(VentaWebUseCase ventas, PasarelaWebpay pasarela, TransaccionesWebpay transacciones,
                    CheckoutProperties props, PlatformTransactionManager txManager) {
        this.ventas = ventas;
        this.pasarela = pasarela;
        this.transacciones = transacciones;
        this.props = props;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public ValidacionCarrito validar(Long clienteId, List<LineaCarrito> lineas) {
        return ventas.validar(clienteId, lineas);
    }

    // ---------- inicio del pago ----------

    @Override
    public ResultadoCheckout iniciar(long clienteId, List<LineaCarrito> lineas, UUID idempotencyKey) {
        PedidoCreado p = tx.execute(s -> ventas.crearPendiente(clienteId, lineas, idempotencyKey, hash(lineas)));
        PedidoCheckout pedido = new PedidoCheckout(p.codigo(), p.estado(), p.total(), p.expiraEn());

        if (p.estado() != EstadoPedido.PENDIENTE_PAGO) {
            return new ResultadoCheckout(pedido, null);   // gratuito (PAGADO) o reintento de uno ya resuelto
        }
        if (p.existente()) {   // reintento con la misma Idempotency-Key: se reenvía el mismo pago
            Optional<TransaccionWebpay> t = tx.execute(s -> transacciones.creadaDelPedido(p.id()));
            if (t.isPresent()) {
                return new ResultadoCheckout(pedido, new PagoIniciado(PROVEEDOR, pasarela.urlFormulario(), t.get().token()));
            }
        }

        String sesion = "S" + clienteId + "-" + p.id();
        InicioWebpay inicio;
        try {
            inicio = pasarela.crear(p.codigo(), sesion, p.total(), props.urlRetorno());   // HTTP, fuera de transacción
        } catch (PasarelaNoDisponibleException e) {
            // Sin pago iniciado no tiene sentido retener stock y cupos: se devuelven de inmediato.
            tx.executeWithoutResult(s -> ventas.cerrarSinPago(p.id(), EstadoPedido.RECHAZADO));
            throw e;
        }
        tx.executeWithoutResult(s -> transacciones.registrar(p.id(), p.codigo(), sesion, inicio.token(), p.total()));
        return new ResultadoCheckout(pedido, new PagoIniciado(PROVEEDOR, inicio.url(), inicio.token()));
    }

    // ---------- retorno desde Webpay ----------

    /**
     * Casos que envía Transbank al {@code return_url}:
     * <ul>
     *   <li>solo {@code token_ws}: flujo normal → commit;</li>
     *   <li>solo {@code TBK_TOKEN}: el cliente anuló en el formulario → ANULADO;</li>
     *   <li>{@code token_ws} y {@code TBK_TOKEN}: error en el formulario → RECHAZADO;</li>
     *   <li>solo {@code TBK_ORDEN_COMPRA}: se agotó el tiempo del formulario → EXPIRADO.</li>
     * </ul>
     */
    @Override
    public String procesarRetorno(String tokenWs, String tbkToken, String tbkOrdenCompra) {
        if (tokenWs != null && tbkToken == null) {
            return confirmar(tokenWs);
        }
        if (tokenWs != null) {
            return cerrarSinPago(tokenWs, tbkOrdenCompra, EstadoTransaccion.ERROR, EstadoPedido.RECHAZADO);
        }
        if (tbkToken != null) {
            return cerrarSinPago(tbkToken, tbkOrdenCompra, EstadoTransaccion.ANULADA_USUARIO, EstadoPedido.ANULADO);
        }
        if (tbkOrdenCompra != null) {
            return cerrarSinPago(null, tbkOrdenCompra, EstadoTransaccion.EXPIRADA, EstadoPedido.EXPIRADO);
        }
        return null;
    }

    private String confirmar(String token) {
        Optional<TransaccionWebpay> previa = tx.execute(s -> transacciones.porToken(token));
        if (previa.isEmpty()) {
            return null;
        }
        String codigo = previa.get().buyOrder();
        if (!previa.get().pendiente()) {
            return codigo;   // retorno repetido (recarga, doble POST): ya está resuelto
        }

        Optional<ResultadoWebpay> resultado;
        try {
            resultado = pasarela.confirmar(token);                // HTTP, fuera de transacción
            if (resultado.isEmpty()) {
                resultado = pasarela.consultar(token);            // p. ej. otro retorno ya hizo el commit
            }
        } catch (PasarelaNoDisponibleException e) {
            log.warn("Webpay no respondió al confirmar el pedido {}; lo resolverá la tarea de vencimiento", codigo);
            return codigo;   // queda PENDIENTE_PAGO: la tarea consultará el estado antes de vencerlo
        }

        Optional<ResultadoWebpay> r = resultado;
        tx.executeWithoutResult(s -> transacciones.bloquearPorToken(token)
                .filter(TransaccionWebpay::pendiente)   // otro retorno lo resolvió mientras tanto
                .ifPresent(t -> aplicarResultado(t, r)));
        return codigo;
    }

    /** Dentro de una transacción, con la fila de la transacción Webpay bloqueada. */
    private void aplicarResultado(TransaccionWebpay t, Optional<ResultadoWebpay> r) {
        if (r.isEmpty()) {
            transacciones.cerrar(t.id(), EstadoTransaccion.ERROR);
            ventas.cerrarSinPago(t.pedidoId(), EstadoPedido.RECHAZADO);
            return;
        }
        boolean aceptado = r.get().aceptablePara(t.monto());
        if (r.get().autorizado() && !aceptado) {
            log.error("Webpay autorizó un monto distinto para el pedido {}: requiere revisión y reembolso manual",
                    t.buyOrder());
        }
        transacciones.aplicar(t.id(), r.get(), aceptado);
        if (!aceptado) {
            ventas.cerrarSinPago(t.pedidoId(), EstadoPedido.RECHAZADO);
        } else if (!ventas.confirmarPago(t.pedidoId())) {
            // TODO(pregunta-abierta): reembolsos. Pago autorizado sobre un pedido que ya no estaba pendiente
            // (su reserva venció justo antes): hoy se registra el pago y se avisa en el log para reembolsar a mano.
            log.error("Pago autorizado para el pedido {} que ya no estaba pendiente: requiere reembolso manual",
                    t.buyOrder());
        }
    }

    private String cerrarSinPago(String token, String buyOrder, EstadoTransaccion estadoTx, EstadoPedido estadoPedido) {
        return tx.execute(s -> {
            Optional<TransaccionWebpay> t = token != null ? transacciones.bloquearPorToken(token) : Optional.empty();
            if (t.isEmpty() && buyOrder != null) {
                t = transacciones.bloquearPorBuyOrder(buyOrder);
            }
            if (t.isEmpty()) {
                return null;
            }
            if (t.get().pendiente()) {
                transacciones.cerrar(t.get().id(), estadoTx);
                ventas.cerrarSinPago(t.get().pedidoId(), estadoPedido);
            }
            return t.get().buyOrder();
        });
    }

    // ---------- vencimiento de reservas ----------

    @Override
    public int expirarVencidos() {
        List<Long> vencidos = tx.execute(s -> ventas.vencidos(LOTE_VENCIDOS));
        int resueltos = 0;
        for (Long pedidoId : vencidos) {
            Optional<TransaccionWebpay> t = tx.execute(s -> transacciones.creadaDelPedido(pedidoId));
            Optional<ResultadoWebpay> estado = Optional.empty();
            if (t.isPresent()) {
                try {
                    estado = pasarela.consultar(t.get().token());   // ¿pagó y su retorno se perdió?
                } catch (PasarelaNoDisponibleException e) {
                    continue;   // sin respuesta de Webpay no se vence: se reintenta en la próxima pasada
                }
            }
            Optional<ResultadoWebpay> r = estado;
            Boolean resuelto = tx.execute(s -> {
                if (t.isEmpty()) {
                    return ventas.cerrarSinPago(pedidoId, EstadoPedido.EXPIRADO);
                }
                Optional<TransaccionWebpay> bloqueada = transacciones.bloquearPorToken(t.get().token())
                        .filter(TransaccionWebpay::pendiente);
                if (bloqueada.isEmpty()) {
                    return false;   // la resolvió un retorno mientras consultábamos
                }
                if (r.isPresent() && r.get().aceptablePara(bloqueada.get().monto())) {
                    aplicarResultado(bloqueada.get(), r);
                } else {
                    transacciones.cerrar(bloqueada.get().id(), EstadoTransaccion.EXPIRADA);
                    ventas.cerrarSinPago(pedidoId, EstadoPedido.EXPIRADO);
                }
                return true;
            });
            if (Boolean.TRUE.equals(resuelto)) {
                resueltos++;
            }
        }
        if (resueltos > 0) {
            log.info("Reservas vencidas resueltas: {}", resueltos);
        }
        return resueltos;
    }

    /** SHA-256 del carrito normalizado: misma Idempotency-Key con otro carrito → idempotency-conflict. */
    static String hash(List<LineaCarrito> lineas) {
        String canonico = lineas.stream()
                .map(l -> l.tipo() + ":" + l.itemId() + ":" + l.cantidad())
                .sorted()
                .collect(Collectors.joining("|"));
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
