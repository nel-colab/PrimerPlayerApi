package cl.primerplayer.pagos.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import cl.primerplayer.pagos.api.PagoInvalidoException;
import cl.primerplayer.pagos.api.PagosMostrador.PagoMostrador;
import cl.primerplayer.pagos.application.port.out.PagoRepositorio;
import cl.primerplayer.shared.api.ExcepcionNegocio;
import cl.primerplayer.shared.api.ValidacionException;

class PagosServiceTest {

    /** Guarda lo que se insertaría. */
    static class PagosEnMemoria implements PagoRepositorio {
        record Insertado(PagoMostrador pago, Long vuelto, long saldo) {}
        final List<Insertado> insertados = new ArrayList<>();

        @Override public void insertarMostrador(long pedidoId, PagoMostrador pago, Long vuelto, long saldo, Instant ahora) {
            insertados.add(new Insertado(pago, vuelto, saldo));
        }

        @Override public List<PagoFila> dePedidos(Collection<Long> pedidoIds) { return List.of(); }
    }

    final PagosEnMemoria repo = new PagosEnMemoria();
    final PagosService service = new PagosService(repo, Clock.fixed(Instant.parse("2026-11-13T18:00:00Z"), ZoneOffset.UTC));

    static PagoMostrador efectivo(long monto, long recibido, Long vuelto) {
        return new PagoMostrador("efectivo", monto, null, null, null, null, null, recibido, vuelto);
    }

    static PagoMostrador debito(long monto, String boleta) {
        return new PagoMostrador("debito", monto, boleta, null, null, null, null, null, null);
    }

    static PagoMostrador carta(long monto, String nombre, String codigo, Long valor) {
        return new PagoMostrador("cartas-tcg", monto, null, null, nombre, codigo, valor, null, null);
    }

    static String code(Throwable t) {
        return ((ExcepcionNegocio) t).code();
    }

    // ---------- casos felices ----------

    @Test
    void efectivo_vueltoCalculadoSiNoSeDeclara() {
        service.registrar(1, 4990, List.of(efectivo(4990, 10000, null)), false);
        assertThat(repo.insertados).singleElement().satisfies(i -> assertThat(i.vuelto()).isEqualTo(5010));
    }

    @Test
    void pagoMixto_efectivoDebitoYCartaConSaldoAFavor() {
        service.registrar(1, 60000, List.of(efectivo(10000, 10000, 0L), debito(30000, "B-1"),
                carta(20000, "Charizard ex", "SV3-199", 25000L)), false);
        assertThat(repo.insertados).hasSize(3);
        assertThat(repo.insertados.get(2).saldo()).isEqualTo(5000);
    }

    @Test
    void notaDeCredito_soloElMonto() {
        service.registrar(1, 3000, List.of(new PagoMostrador("nota-credito", 3000, null, null, null, null, null, null, null)), false);
        assertThat(repo.insertados).hasSize(1);
    }

    @Test
    void totalCero_sinPagos() {
        service.registrar(1, 0, List.of(), false);
        assertThat(repo.insertados).isEmpty();
        assertThatThrownBy(() -> service.registrar(1, 0, List.of(debito(100, "B")), false))
                .satisfies(t -> assertThat(code(t)).isEqualTo("payments-mismatch"));
    }

    // ---------- errores de negocio ----------

    @Test
    void sumaDistintaAlTotal_paymentsMismatch() {
        assertThatThrownBy(() -> service.registrar(1, 5000, List.of(debito(4000, "B")), false))
                .isInstanceOf(PagoInvalidoException.class).satisfies(t -> assertThat(code(t)).isEqualTo("payments-mismatch"));
        assertThatThrownBy(() -> service.registrar(1, 5000, List.of(), false))
                .satisfies(t -> assertThat(code(t)).isEqualTo("payments-mismatch"));
        assertThat(repo.insertados).isEmpty();
    }

    @Test
    void tarjetaSinBoleta_receiptRequired() {
        for (String metodo : List.of("debito", "credito", "prepago")) {
            var p = new PagoMostrador(metodo, 1000, " ", null, null, null, null, null, null);
            assertThatThrownBy(() -> service.registrar(1, 1000, List.of(p), false))
                    .satisfies(t -> assertThat(code(t)).isEqualTo("receipt-required"));
        }
    }

    @Test
    void transferenciaSinComprobante_transferProofRequired() {
        var sin = new PagoMostrador("transferencia", 1000, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.registrar(1, 1000, List.of(sin), false))
                .satisfies(t -> assertThat(code(t)).isEqualTo("transfer-proof-required"));
        var con = new PagoMostrador("transferencia", 1000, null, UUID.randomUUID(), null, null, null, null, null);
        service.registrar(1, 1000, List.of(con), false);
        assertThat(repo.insertados).hasSize(1);
    }

    @Test
    void cartaIncompletaOMenorAlMonto() {
        assertThatThrownBy(() -> service.registrar(1, 1000, List.of(carta(1000, "Pikachu", null, 1000L)), false))
                .satisfies(t -> assertThat(code(t)).isEqualTo("tcg-card-required"));
        assertThatThrownBy(() -> service.registrar(1, 1000, List.of(carta(1000, "Pikachu", "X-1", 500L)), false))
                .isInstanceOf(ValidacionException.class);
    }

    @Test
    void vueltoDistinto_exigeConfirmacion() {
        var p = efectivo(4990, 10000, 5000L);   // calculado: 5010
        assertThatThrownBy(() -> service.registrar(1, 4990, List.of(p), false))
                .satisfies(t -> assertThat(code(t)).isEqualTo("cash-change-mismatch"));
        service.registrar(1, 4990, List.of(p), true);
        assertThat(repo.insertados).singleElement().satisfies(i -> assertThat(i.vuelto()).isEqualTo(5000));
    }

    @Test
    void efectivoInsuficiente_validation() {
        assertThatThrownBy(() -> service.registrar(1, 4990, List.of(efectivo(4990, 2000, null)), false))
                .isInstanceOf(ValidacionException.class);
    }

    @Test
    void metodosNoAceptadosEnMostrador() {
        for (String metodo : List.of("tarjeta-credito", "webpay", "bitcoin")) {
            var p = new PagoMostrador(metodo, 1000, null, null, null, null, null, null, null);
            assertThatThrownBy(() -> service.registrar(1, 1000, List.of(p), false)).isInstanceOf(ValidacionException.class);
        }
        assertThatThrownBy(() -> service.registrar(1, 1000, List.of(debito(0, "B")), false))
                .isInstanceOf(ValidacionException.class);
    }
}
