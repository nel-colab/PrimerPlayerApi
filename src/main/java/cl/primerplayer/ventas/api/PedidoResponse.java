package cl.primerplayer.ventas.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import cl.primerplayer.pagos.api.ConsultarPagosUseCase.PagoVista;
import cl.primerplayer.shared.api.FechasTienda;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * JSON de un pedido: forma del {@code PurchaseOrder} del frontend + estado, fechas y pagos. Vive en {@code api} porque
 * lo devuelven "mis pedidos", el panel de pedidos y el POS (una sola definición, sin copias que se desalineen).
 */
public record PedidoResponse(
        @Schema(example = "PP-2026-000123") String code,
        @Schema(example = "PAGADO", description = "PENDIENTE_PAGO | PAGADO | LISTO_RETIRO | ENTREGADO | RECHAZADO | "
                + "EXPIRADO | ANULADO | REEMBOLSADO") String status,
        @Schema(example = "web", description = "web | tienda") String channel,
        @Schema(description = "CLP") long total,
        @Schema(description = "Correo del cliente") String email,
        String customerName,
        @Schema(nullable = true, description = "Teléfono del perfil del cliente; null en ventas de mostrador")
        String phone,
        @Schema(nullable = true, description = "RUT del perfil del cliente; null en ventas de mostrador")
        String rut,
        @Schema(description = "Nombre del vendedor (solo ventas de mostrador)") String seller,
        OffsetDateTime createdAt,
        OffsetDateTime paidAt,
        OffsetDateTime updatedAt,
        List<Item> items,
        @Schema(description = "Pagos registrados (vacío si aún no se paga). Pago mixto = varios elementos") List<Payment> payments) {

    public record Item(
            @Schema(description = "product | event") String type,
            long itemId,
            String name,
            int quantity,
            long unitPrice,
            long subtotal) {}

    /** Mismo formato que {@code Payment} del frontend ({@code method}, {@code amount}, {@code details}). */
    public record Payment(
            @Schema(example = "efectivo", description = "efectivo | debito | credito | prepago | transferencia | "
                    + "cartas-tcg | nota-credito | tarjeta-credito (antiguo) | webpay") String method,
            long amount,
            PaymentDetails details,
            OffsetDateTime createdAt) {}

    /** Solo vienen los campos del método correspondiente; el resto es {@code null}. */
    public record PaymentDetails(
            @Schema(description = "débito, crédito y prepago") String receiptNumber,
            @Schema(description = "transferencia: id del comprobante subido") UUID transferProofId,
            @Schema(description = "cartas-tcg") TcgCard tcgCard,
            @Schema(description = "cartas-tcg: valor de la carta − monto cubierto") Long creditBalance,
            @Schema(description = "efectivo") Long cashReceived,
            @Schema(description = "efectivo") Long changeGiven,
            @Schema(description = "webpay") String authorizationCode,
            @Schema(description = "webpay: últimos 4 dígitos") String cardLast4,
            @Schema(description = "webpay: VD débito, VN crédito, VC cuotas, …") String paymentType,
            @Schema(description = "webpay") Integer installments) {}

    public record TcgCard(String name, String code, long value) {}

    public static PedidoResponse de(PedidoVista p) {
        return new PedidoResponse(p.codigo(), p.estado().name(), p.canal().valor(), p.total(), p.clienteEmail(),
                p.clienteNombre(), p.clienteTelefono(), p.clienteRut(), p.vendedorNombre(),
                FechasTienda.enZona(p.creadoEn()), FechasTienda.enZona(p.pagadoEn()),
                FechasTienda.enZona(p.actualizadoEn()),
                p.items().stream().map(i -> new Item(i.tipo(), i.itemId(), i.nombre(), i.cantidad(), i.precioUnitario(),
                        i.subtotal())).toList(),
                p.pagos().stream().map(PedidoResponse::pago).toList());
    }

    private static Payment pago(PagoVista v) {
        boolean carta = "cartas-tcg".equals(v.metodo());
        return new Payment(v.metodo(), v.monto(), new PaymentDetails(
                v.numeroBoleta(), v.comprobanteId(),
                carta ? new TcgCard(v.cartaNombre(), v.cartaCodigo(), v.cartaValor()) : null,
                carta ? v.saldoAFavor() : null,
                v.efectivoRecibido(), v.vueltoEntregado(),
                v.autorizacionWebpay(), v.tarjetaUltimos4(), v.tipoPagoWebpay(), v.cuotas()),
                FechasTienda.enZona(v.creadoEn()));
    }
}
