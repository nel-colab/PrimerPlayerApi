package cl.primerplayer.ventas.api;

import java.time.Instant;
import java.util.List;

import cl.primerplayer.pagos.api.ConsultarPagosUseCase.PagoVista;

/**
 * Pedido listo para mostrar: incluye datos de contacto del cliente y nombre del vendedor (resueltos en identidad).
 * Los nombres de ítems y precios son el snapshot del momento de la compra.
 */
public record PedidoVista(
        long id,
        String codigo,
        CanalPedido canal,
        EstadoPedido estado,
        long total,
        long clienteId,
        String clienteEmail,
        String clienteNombre,
        String clienteTelefono,
        String clienteRut,
        Long vendedorId,
        String vendedorNombre,
        Instant creadoEn,
        Instant pagadoEn,
        Instant actualizadoEn,
        List<Item> items,
        List<PagoVista> pagos) {

    public PedidoVista {
        items = List.copyOf(items);
        pagos = pagos == null ? List.of() : List.copyOf(pagos);
    }

    /** @param tipo {@code product} o {@code event}; {@code itemId} es el producto o el evento */
    public record Item(String tipo, long itemId, String nombre, int cantidad, long precioUnitario, long subtotal) {}
}
