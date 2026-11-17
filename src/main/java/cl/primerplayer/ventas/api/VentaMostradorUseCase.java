package cl.primerplayer.ventas.api;

import java.util.List;
import java.util.UUID;

import cl.primerplayer.ventas.api.VentaWebUseCase.LineaCarrito;
import cl.primerplayer.ventas.api.VentaWebUseCase.PedidoCreado;
import cl.primerplayer.ventas.api.VentaWebUseCase.ValidacionCarrito;

/**
 * Venta de mostrador (la usa {@code pos}). La venta queda a nombre del cliente anónimo {@code mostrador} y no acumula
 * límite por cliente. Los productos se entregan en el momento (ENTREGADO); si solo hay cupos de eventos, queda PAGADO.
 */
public interface VentaMostradorUseCase {

    /** Igual que el carrito web, sin límite por cliente. Nunca lanza errores por línea: van en {@code errores}. */
    ValidacionCarrito validar(List<LineaCarrito> lineas);

    /**
     * Crea la venta: valida, descuenta stock (kardex VENTA_MOSTRADOR), ocupa cupos e inscribe (CONFIRMADA). No registra
     * los pagos: eso lo hace el módulo pagos en la misma transacción. Misma clave + mismo hash → la venta existente.
     *
     * @throws CarritoInvalidoException errores por línea (empty, invalid-quantity, item-unavailable, out-of-stock, ...)
     * @throws IdempotenciaConflictoException misma clave con otra venta
     */
    PedidoCreado crear(long vendedorId, List<LineaCarrito> lineas, UUID idempotencyKey, String hashCuerpo);

    /**
     * Anula una venta de mostrador (PAGADO o ENTREGADO → ANULADO): repone stock (kardex ANULACION) y libera cupos.
     * Los pagos quedan registrados: el dinero se devuelve en el mostrador.
     *
     * @throws cl.primerplayer.shared.api.RecursoNoEncontradoException si el código no existe
     * @throws EstadoPedidoInvalidoException si no es una venta de mostrador vigente
     */
    PedidoVista anular(String codigo);
}
