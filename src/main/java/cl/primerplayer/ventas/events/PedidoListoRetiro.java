package cl.primerplayer.ventas.events;

import java.util.List;

/** Un pedido web quedó listo para retiro y debe notificarse al cliente. */
public record PedidoListoRetiro(long pedidoId, String codigo, long clienteId, List<Item> items) {

    public PedidoListoRetiro {
        items = List.copyOf(items);
    }

    public record Item(String tipo, String nombre, int cantidad) {}
}
