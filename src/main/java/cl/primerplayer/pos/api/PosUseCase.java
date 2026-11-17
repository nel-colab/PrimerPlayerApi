package cl.primerplayer.pos.api;

import java.util.List;
import java.util.UUID;

import cl.primerplayer.pagos.api.PagosMostrador.PagoMostrador;
import cl.primerplayer.ventas.api.PedidoVista;
import cl.primerplayer.ventas.api.VentaWebUseCase.LineaCarrito;
import cl.primerplayer.ventas.api.VentaWebUseCase.ValidacionCarrito;

/** Puerto de entrada del mostrador. */
public interface PosUseCase {

    /** Revisa la venta antes de cobrar (precios, stock, cupos). Sin límite por cliente. */
    ValidacionCarrito validar(List<LineaCarrito> lineas);

    /**
     * Registra la venta y sus pagos en una transacción. Misma {@code Idempotency-Key} + mismo cuerpo → la misma venta.
     *
     * @throws cl.primerplayer.ventas.api.CarritoInvalidoException errores por línea
     * @throws cl.primerplayer.pagos.api.PagoInvalidoException errores de pago (422)
     * @throws cl.primerplayer.ventas.api.IdempotenciaConflictoException misma clave con otra venta
     */
    PedidoVista vender(long vendedorId, List<LineaCarrito> lineas, List<PagoMostrador> pagos,
                       boolean confirmarVueltoDistinto, UUID idempotencyKey);

    /** Anula una venta de mostrador: repone stock y cupos (solo ADMIN, controlado en la web). */
    PedidoVista anular(String codigo);
}
