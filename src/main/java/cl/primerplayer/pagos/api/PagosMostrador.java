package cl.primerplayer.pagos.api;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pagos de una venta de mostrador (pago mixto). Exige la transacción de quien orquesta (propagation MANDATORY): el
 * pedido, el stock y los pagos se confirman juntos, y el trigger diferido de la base verifica Σ pagos = total.
 */
public interface PagosMostrador {

    /** Métodos aceptados en el mostrador. {@code tarjeta-credito} es obsoleto y {@code webpay} es solo web. */
    Set<String> METODOS = Set.of("efectivo", "debito", "credito", "prepago", "transferencia", "cartas-tcg", "nota-credito");

    /**
     * Valida y registra los pagos.
     *
     * @param confirmarVueltoDistinto el vendedor confirma que entregó un vuelto distinto al calculado
     * @throws PagoInvalidoException 422 {@code payments-mismatch}, {@code receipt-required},
     *         {@code transfer-proof-required}, {@code tcg-card-required} o {@code cash-change-mismatch}
     * @throws cl.primerplayer.shared.api.ValidacionException 400 por método no aceptado o montos inválidos
     */
    void registrar(long pedidoId, long total, List<PagoMostrador> pagos, boolean confirmarVueltoDistinto);

    /**
     * Un pago de la venta. Los campos opcionales dependen del método:
     * boleta (débito, crédito, prepago), comprobante (transferencia), carta (cartas-tcg), efectivo recibido y vuelto.
     *
     * @param vueltoEntregado {@code null} = el calculado (recibido − monto)
     */
    record PagoMostrador(String metodo, long monto, String numeroBoleta, UUID comprobanteId, String cartaNombre,
                         String cartaCodigo, Long cartaValor, Long efectivoRecibido, Long vueltoEntregado) {}
}
