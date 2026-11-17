package cl.primerplayer.ventas.application.port.out;

import java.time.Instant;
import java.util.Optional;

import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.ventas.api.ConsultarPedidosUseCase.FiltroPedidos;
import cl.primerplayer.ventas.api.EstadoPedido;
import cl.primerplayer.ventas.domain.Pedido;

/** Puerto de salida: persistencia de pedidos. */
public interface PedidoRepositorio {

    Pagina<Pedido> delCliente(long clienteId, int page, int size);

    Optional<Pedido> porCodigo(String codigo);

    Optional<Pedido> porId(long id);

    Pagina<Pedido> buscar(FiltroPedidos filtro);

    /** PAGADO, LISTO_RETIRO y ENTREGADO creados en {@code [desde, hasta)}, en orden cronológico. */
    java.util.List<Pedido> validosEntre(Instant desde, Instant hasta);

    /**
     * Cambia el estado solo si sigue siendo {@code esperado} (UPDATE condicional atómico).
     *
     * @return {@code false} si otro proceso lo cambió antes
     */
    boolean cambiarEstado(long id, EstadoPedido esperado, EstadoPedido nuevo, Instant ahora);
}
