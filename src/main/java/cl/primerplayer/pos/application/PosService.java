package cl.primerplayer.pos.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.pagos.api.PagosMostrador;
import cl.primerplayer.pagos.api.PagosMostrador.PagoMostrador;
import cl.primerplayer.pos.api.PosUseCase;
import cl.primerplayer.ventas.api.ConsultarPedidosUseCase;
import cl.primerplayer.ventas.api.PedidoVista;
import cl.primerplayer.ventas.api.VentaMostradorUseCase;
import cl.primerplayer.ventas.api.VentaWebUseCase.LineaCarrito;
import cl.primerplayer.ventas.api.VentaWebUseCase.ValidacionCarrito;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class PosService implements PosUseCase {

    private final VentaMostradorUseCase ventas;
    private final PagosMostrador pagos;
    private final ConsultarPedidosUseCase pedidos;

    @Override
    public ValidacionCarrito validar(List<LineaCarrito> lineas) {
        return ventas.validar(lineas);
    }

    /** Una sola transacción: si un pago no cuadra, no se descuenta stock ni se crea la venta. */
    @Override
    @Transactional
    public PedidoVista vender(long vendedorId, List<LineaCarrito> lineas, List<PagoMostrador> listaPagos,
                             boolean confirmarVueltoDistinto, UUID idempotencyKey) {
        var venta = ventas.crear(vendedorId, lineas, idempotencyKey, hash(lineas, listaPagos));
        if (!venta.existente()) {   // un reintento con la misma clave no registra los pagos dos veces
            pagos.registrar(venta.id(), venta.total(), listaPagos, confirmarVueltoDistinto);
        }
        return pedidos.porCodigo(venta.codigo());
    }

    @Override
    public PedidoVista anular(String codigo) {
        return ventas.anular(codigo);
    }

    /** SHA-256 de líneas + pagos: misma clave con otra venta (o con otros pagos) → idempotency-conflict. */
    static String hash(List<LineaCarrito> lineas, List<PagoMostrador> pagos) {
        String canonico = Stream.concat(
                lineas.stream().map(l -> l.tipo() + ":" + l.itemId() + ":" + l.cantidad()).sorted(),
                (pagos == null ? List.<PagoMostrador>of() : pagos).stream().map(p -> String.join(":", p.metodo(),
                        String.valueOf(p.monto()), Objects.toString(p.numeroBoleta(), ""),
                        Objects.toString(p.comprobanteId(), ""), Objects.toString(p.cartaCodigo(), ""),
                        Objects.toString(p.cartaValor(), ""), Objects.toString(p.efectivoRecibido(), ""),
                        Objects.toString(p.vueltoEntregado(), ""))))
                .collect(Collectors.joining("|"));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
