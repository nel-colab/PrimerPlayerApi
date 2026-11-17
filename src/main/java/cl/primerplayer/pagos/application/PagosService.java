package cl.primerplayer.pagos.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.pagos.api.ConsultarPagosUseCase;
import cl.primerplayer.pagos.api.PagoInvalidoException;
import cl.primerplayer.pagos.api.PagosMostrador;
import cl.primerplayer.pagos.application.port.out.PagoRepositorio;
import cl.primerplayer.pagos.application.port.out.PagoRepositorio.ComprobanteInexistenteException;
import cl.primerplayer.shared.api.ValidacionException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class PagosService implements PagosMostrador, ConsultarPagosUseCase {

    private final PagoRepositorio pagos;
    private final Clock clock;

    // ---------- pagos de mostrador ----------

    @Override
    @Transactional(propagation = Propagation.MANDATORY)   // la abre el POS, junto con el pedido y el stock
    public void registrar(long pedidoId, long total, List<PagoMostrador> lista, boolean confirmarVueltoDistinto) {
        List<PagoMostrador> recibidos = lista == null ? List.of() : lista;
        if (total == 0) {
            if (!recibidos.isEmpty()) {
                throw new PagoInvalidoException("payments-mismatch", "Una venta de total $0 no lleva pagos.", null);
            }
            return;
        }
        if (recibidos.isEmpty()) {
            throw new PagoInvalidoException("payments-mismatch", "Falta registrar el pago de la venta.", null);
        }

        long suma = 0;
        long[] vueltos = new long[recibidos.size()];
        for (int i = 0; i < recibidos.size(); i++) {
            PagoMostrador p = recibidos.get(i);
            validarForma(i, p);
            vueltos[i] = validarMetodo(i, p, confirmarVueltoDistinto);
            suma += p.monto();
        }
        if (suma != total) {
            throw new PagoInvalidoException("payments-mismatch",
                    "Los pagos suman $%d y el total de la venta es $%d.".formatted(suma, total), null);
        }

        Instant ahora = clock.instant();
        for (int i = 0; i < recibidos.size(); i++) {
            PagoMostrador p = recibidos.get(i);
            try {
                pagos.insertarMostrador(pedidoId, p, "efectivo".equals(p.metodo()) ? vueltos[i] : null,
                        "cartas-tcg".equals(p.metodo()) ? p.cartaValor() - p.monto() : 0, ahora);
            } catch (ComprobanteInexistenteException e) {
                throw new PagoInvalidoException("transfer-proof-required",
                        "El comprobante de transferencia no existe: súbelo de nuevo.", i);
            }
        }
    }

    private static void validarForma(int i, PagoMostrador p) {
        if (p.metodo() == null || !METODOS.contains(p.metodo())) {
            throw new ValidacionException("pagos[" + i + "].method",
                    "Método no aceptado en el mostrador. Use: " + String.join(", ", METODOS.stream().sorted().toList()) + ".");
        }
        if (p.monto() <= 0) {
            throw new ValidacionException("pagos[" + i + "].amount", "El monto debe ser mayor que 0.");
        }
    }

    /** @return vuelto a guardar (solo efectivo; 0 en el resto) */
    private static long validarMetodo(int i, PagoMostrador p, boolean confirmarVueltoDistinto) {
        switch (p.metodo()) {
            case "debito", "credito", "prepago" -> {
                if (p.numeroBoleta() == null || p.numeroBoleta().isBlank()) {
                    throw new PagoInvalidoException("receipt-required",
                            "Ingresa el número de boleta del pago con " + p.metodo() + ".", i);
                }
                if (p.numeroBoleta().strip().length() > 30) {
                    throw new ValidacionException("pagos[" + i + "].details.receiptNumber", "Máximo 30 caracteres.");
                }
            }
            case "transferencia" -> {
                if (p.comprobanteId() == null) {
                    throw new PagoInvalidoException("transfer-proof-required",
                            "Sube el comprobante de la transferencia antes de cobrar.", i);
                }
            }
            case "cartas-tcg" -> {
                if (vacio(p.cartaNombre()) || vacio(p.cartaCodigo()) || p.cartaValor() == null) {
                    throw new PagoInvalidoException("tcg-card-required",
                            "Ingresa nombre, código y valor de la carta recibida.", i);
                }
                if (p.cartaValor() < p.monto()) {
                    throw new ValidacionException("pagos[" + i + "].details.tcgCard.value",
                            "El valor de la carta no puede ser menor que el monto que cubre.");
                }
            }
            case "efectivo" -> {
                if (p.efectivoRecibido() == null || p.efectivoRecibido() < p.monto()) {
                    throw new ValidacionException("pagos[" + i + "].details.cashReceived",
                            "El efectivo recibido debe cubrir al menos el monto del pago.");
                }
                long calculado = p.efectivoRecibido() - p.monto();
                long declarado = p.vueltoEntregado() == null ? calculado : p.vueltoEntregado();
                if (declarado < 0) {
                    throw new ValidacionException("pagos[" + i + "].details.changeGiven", "El vuelto no puede ser negativo.");
                }
                if (declarado != calculado && !confirmarVueltoDistinto) {
                    throw new PagoInvalidoException("cash-change-mismatch",
                            "El vuelto declarado ($%d) no coincide con el calculado ($%d). Confirma si es correcto."
                                    .formatted(declarado, calculado), i);
                }
                return declarado;
            }
            default -> { /* nota-credito: solo el monto */ }
        }
        return 0;
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    // ---------- lectura ----------

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<PagoVista>> dePedidos(Collection<Long> pedidoIds) {
        if (pedidoIds.isEmpty()) {
            return Map.of();
        }
        return pagos.dePedidos(pedidoIds).stream().collect(Collectors.groupingBy(PagoRepositorio.PagoFila::pedidoId,
                Collectors.mapping(PagoRepositorio.PagoFila::pago, Collectors.toList())));
    }
}
