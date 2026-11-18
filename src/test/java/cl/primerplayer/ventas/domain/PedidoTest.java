package cl.primerplayer.ventas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import cl.primerplayer.ventas.api.CanalPedido;
import cl.primerplayer.ventas.api.EstadoPedido;
import cl.primerplayer.ventas.api.EstadoPedidoInvalidoException;

class PedidoTest {

    static final Pedido.Item PRODUCTO = new Pedido.Item("product", 1, "Sobre", 2, 4990, 9980);
    static final Pedido.Item EVENTO = new Pedido.Item("event", 7, "League Cup", 1, 7000, 7000);

    static Pedido pedido(CanalPedido canal, EstadoPedido estado, List<Pedido.Item> items) {
        Instant t = Instant.parse("2026-11-13T15:00:00Z");
        return new Pedido(1, "PP-2026-000001", 10, canal == CanalPedido.TIENDA ? 20L : null, canal, estado, 9980,
                t, t, t, items);
    }

    @Test
    void retiro_flujoNormalPagadoListoEntregado() {
        assertThatCode(() -> pedido(CanalPedido.WEB, EstadoPedido.PAGADO, List.of(PRODUCTO)).validarRetiro(EstadoPedido.LISTO_RETIRO))
                .doesNotThrowAnyException();
        assertThatCode(() -> pedido(CanalPedido.WEB, EstadoPedido.LISTO_RETIRO, List.of(PRODUCTO)).validarRetiro(EstadoPedido.ENTREGADO))
                .doesNotThrowAnyException();
        // El cliente puede llegar antes de que lo marquen listo: pagado → entregado directo
        assertThatCode(() -> pedido(CanalPedido.WEB, EstadoPedido.PAGADO, List.of(PRODUCTO)).validarRetiro(EstadoPedido.ENTREGADO))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPedido.class, names = {"PENDIENTE_PAGO", "RECHAZADO", "EXPIRADO", "ANULADO", "REEMBOLSADO", "ENTREGADO"})
    void retiro_desdeEstadosQueNoLoPermiten_orderInvalidState(EstadoPedido estado) {
        assertThatThrownBy(() -> pedido(CanalPedido.WEB, estado, List.of(PRODUCTO)).validarRetiro(EstadoPedido.LISTO_RETIRO))
                .isInstanceOf(EstadoPedidoInvalidoException.class)
                .extracting(e -> ((EstadoPedidoInvalidoException) e).code()).isEqualTo("order-invalid-state");
    }

    @Test
    void retiro_noVuelveAtrasDeListoAListo() {
        assertThatThrownBy(() -> pedido(CanalPedido.WEB, EstadoPedido.LISTO_RETIRO, List.of(PRODUCTO)).validarRetiro(EstadoPedido.LISTO_RETIRO))
                .isInstanceOf(EstadoPedidoInvalidoException.class);
    }

    @Test
    void retiro_pedidoSoloDeEventosPuedeMarcarseListo() {
        assertThatCode(() -> pedido(CanalPedido.WEB, EstadoPedido.PAGADO, List.of(EVENTO)).validarRetiro(EstadoPedido.LISTO_RETIRO))
                .doesNotThrowAnyException();
        assertThatCode(() -> pedido(CanalPedido.WEB, EstadoPedido.PAGADO, List.of(PRODUCTO, EVENTO)).validarRetiro(EstadoPedido.LISTO_RETIRO))
                .doesNotThrowAnyException();
    }

    @Test
    void retiro_ventaDeMostrador_seEntregaEnElMomento() {
        assertThatThrownBy(() -> pedido(CanalPedido.TIENDA, EstadoPedido.PAGADO, List.of(PRODUCTO)).validarRetiro(EstadoPedido.ENTREGADO))
                .isInstanceOf(EstadoPedidoInvalidoException.class).hasMessageContaining("mostrador");
    }

    @Test
    void cicloDeVida_transicionesDelPagoYTerminales() {
        assertThat(Pedido.puedePasar(EstadoPedido.PENDIENTE_PAGO, EstadoPedido.PAGADO)).isTrue();
        assertThat(Pedido.puedePasar(EstadoPedido.PENDIENTE_PAGO, EstadoPedido.EXPIRADO)).isTrue();
        assertThat(Pedido.puedePasar(EstadoPedido.PAGADO, EstadoPedido.PENDIENTE_PAGO)).isFalse();
        for (EstadoPedido terminal : List.of(EstadoPedido.RECHAZADO, EstadoPedido.EXPIRADO, EstadoPedido.ANULADO, EstadoPedido.REEMBOLSADO)) {
            for (EstadoPedido otro : EstadoPedido.values()) {
                assertThat(Pedido.puedePasar(terminal, otro)).as(terminal + " → " + otro).isFalse();
            }
        }
    }
}
