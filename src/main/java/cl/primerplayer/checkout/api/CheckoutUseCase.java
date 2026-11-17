package cl.primerplayer.checkout.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import cl.primerplayer.ventas.api.EstadoPedido;
import cl.primerplayer.ventas.api.VentaWebUseCase.LineaCarrito;
import cl.primerplayer.ventas.api.VentaWebUseCase.ValidacionCarrito;

/** Puerto de entrada del checkout web con Webpay Plus. */
public interface CheckoutUseCase {

    /** Revisa el carrito sin reservar nada. @param clienteId {@code null} = visitante */
    ValidacionCarrito validar(Long clienteId, List<LineaCarrito> lineas);

    /**
     * Crea el pedido (reserva stock y cupos por {@code pp.checkout.reserva-minutos}) e inicia el pago en Webpay.
     * Pedidos de total 0 quedan PAGADO sin Webpay ({@code pago = null}).
     *
     * @throws cl.primerplayer.pagos.api.PasarelaNoDisponibleException 502 si Webpay no responde (el pedido se anula y
     *         el stock vuelve)
     */
    ResultadoCheckout iniciar(long clienteId, List<LineaCarrito> lineas, UUID idempotencyKey);

    /**
     * Procesa el regreso del cliente desde Webpay (parámetros tal como los envía Transbank).
     *
     * @return código del pedido para mostrar el resultado; {@code null} si no se reconoce la transacción
     */
    String procesarRetorno(String tokenWs, String tbkToken, String tbkOrdenCompra);

    /** Vence las reservas sin pago (consultando antes a Webpay). @return cuántos pedidos se resolvieron */
    int expirarVencidos();

    record ResultadoCheckout(PedidoCheckout pedido, PagoIniciado pago) {}

    record PedidoCheckout(String codigo, EstadoPedido estado, long total, Instant expiraEn) {}

    /** Para pagar: el navegador hace POST a {@code url} con el campo {@code token_ws = token}. */
    record PagoIniciado(String proveedor, String url, String token) {}
}
