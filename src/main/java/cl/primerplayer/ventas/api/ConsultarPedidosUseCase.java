package cl.primerplayer.ventas.api;

import java.time.Instant;
import java.util.Set;

import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.ValidacionException;

/** Puerto de entrada: lectura de pedidos para el cliente ("mis pedidos") y para la tienda (panel). */
public interface ConsultarPedidosUseCase {

    int MAX_PAGE_SIZE = 100;

    /** Pedidos del cliente, más recientes primero. */
    Pagina<PedidoVista> delCliente(long clienteId, int page, int size);

    /** @throws cl.primerplayer.shared.api.RecursoNoEncontradoException si no existe o es de otro cliente (no revela existencia) */
    PedidoVista delCliente(long clienteId, String codigo);

    /** Panel de la tienda, más recientes primero. */
    Pagina<PedidoVista> buscar(FiltroPedidos filtro);

    /** Cualquier pedido por código (tienda). @throws cl.primerplayer.shared.api.RecursoNoEncontradoException */
    PedidoVista porCodigo(String codigo);

    /**
     * Ventas válidas (PAGADO, LISTO_RETIRO, ENTREGADO) creadas en {@code [desde, hasta)}, con ítems y pagos, en orden
     * cronológico. Base de la caja y de los reportes. Sin nombres de cliente (no hacen falta para sumar).
     */
    java.util.List<PedidoVista> validasEntre(Instant desde, Instant hasta);

    /**
     * @param desde    inclusive ({@code null} = sin límite)
     * @param hasta    exclusivo ({@code null} = sin límite)
     * @param estados  se combinan con OR
     * @param codigo   parte del código, sin importar mayúsculas (para encontrar el pedido al retirar)
     */
    record FiltroPedidos(Instant desde, Instant hasta, CanalPedido canal, Set<EstadoPedido> estados, Long vendedorId,
                         String codigo, int page, int size) {

        public FiltroPedidos {
            estados = estados == null ? Set.of() : Set.copyOf(estados);
            codigo = codigo == null || codigo.isBlank() ? null : codigo.strip();
            validarPagina(page, size);
            if (desde != null && hasta != null && !desde.isBefore(hasta)) {
                throw new ValidacionException("desde", "desde debe ser anterior a hasta.");
            }
        }
    }

    static void validarPagina(int page, int size) {
        if (page < 0) {
            throw new ValidacionException("page", "Debe ser 0 o mayor.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ValidacionException("size", "Debe estar entre 1 y " + MAX_PAGE_SIZE + ".");
        }
    }
}
