package cl.primerplayer.ventas.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import cl.primerplayer.identidad.api.ConsultarUsuarioUseCase;
import cl.primerplayer.identidad.api.UsuarioResumen;
import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import cl.primerplayer.shared.api.Rol;
import cl.primerplayer.shared.api.ValidacionException;
import cl.primerplayer.ventas.api.CanalPedido;
import cl.primerplayer.ventas.api.ConsultarPedidosUseCase.FiltroPedidos;
import cl.primerplayer.ventas.api.EstadoPedido;
import cl.primerplayer.ventas.api.EstadoPedidoInvalidoException;
import cl.primerplayer.ventas.application.port.out.PedidoRepositorio;
import cl.primerplayer.ventas.domain.Pedido;

class PedidosServiceTest {

    static final Instant AHORA = Instant.parse("2026-11-13T18:00:00Z");

    static class PedidosEnMemoria implements PedidoRepositorio {
        final List<Pedido> pedidos = new ArrayList<>();
        boolean otroProcesoGana;

        @Override public Pagina<Pedido> delCliente(long clienteId, int page, int size) {
            List<Pedido> mios = pedidos.stream().filter(p -> p.clienteId() == clienteId).toList();
            return Pagina.de(mios, page, size, mios.size());
        }

        @Override public Optional<Pedido> porCodigo(String codigo) {
            return pedidos.stream().filter(p -> p.codigo().equalsIgnoreCase(codigo)).findFirst();
        }

        @Override public Pagina<Pedido> buscar(FiltroPedidos filtro) { throw new UnsupportedOperationException(); }

        @Override public List<Pedido> validosEntre(Instant desde, Instant hasta) { return List.of(); }

        @Override public Optional<Pedido> porId(long id) {
            return pedidos.stream().filter(p -> p.id() == id).findFirst();
        }

        @Override public boolean cambiarEstado(long id, EstadoPedido esperado, EstadoPedido nuevo, Instant ahora) {
            if (otroProcesoGana) {
                return false;
            }
            for (int i = 0; i < pedidos.size(); i++) {
                Pedido p = pedidos.get(i);
                if (p.id() == id && p.estado() == esperado) {
                    pedidos.set(i, new Pedido(p.id(), p.codigo(), p.clienteId(), p.vendedorId(), p.canal(), nuevo,
                            p.total(), p.creadoEn(), p.pagadoEn(), ahora, p.items()));
                    return true;
                }
            }
            return false;
        }
    }

    static class UsuariosFalsos implements ConsultarUsuarioUseCase {
        final Map<Long, UsuarioResumen> todos = Map.of(
                10L, new UsuarioResumen(10, "cliente@test.cl", "Cliente", Rol.CLIENTE),
                30L, new UsuarioResumen(30, "otro@test.cl", "Otro", Rol.CLIENTE));

        @Override public Optional<UsuarioResumen> buscarActivo(long id) { return Optional.ofNullable(todos.get(id)); }

        @Override public Map<Long, UsuarioResumen> buscarPorIds(Collection<Long> ids) {
            return ids.stream().filter(todos::containsKey).collect(Collectors.toMap(id -> id, todos::get));
        }

        @Override public boolean puedeComprar(long id) { return todos.containsKey(id); }

        @Override public long clienteMostradorId() { return 1; }
    }

    final PedidosEnMemoria repo = new PedidosEnMemoria();
    final PedidosService service = new PedidosService(repo, new UsuariosFalsos(), ids -> Map.of(),
            Clock.fixed(AHORA, ZoneOffset.UTC));

    Pedido web(long id, String codigo, long cliente, EstadoPedido estado) {
        Instant t = AHORA.minusSeconds(3600);
        Pedido p = new Pedido(id, codigo, cliente, null, CanalPedido.WEB, estado, 4990, t, t, t,
                List.of(new Pedido.Item("product", 1, "Sobre", 1, 4990, 4990)));
        repo.pedidos.add(p);
        return p;
    }

    @Test
    void misPedidos_traeCorreoYNombreDelCliente() {
        web(1, "PP-2026-000001", 10, EstadoPedido.PAGADO);
        web(2, "PP-2026-000002", 30, EstadoPedido.PAGADO);

        var pagina = service.delCliente(10, 0, 10);
        assertThat(pagina.content()).singleElement().satisfies(p -> {
            assertThat(p.codigo()).isEqualTo("PP-2026-000001");
            assertThat(p.clienteEmail()).isEqualTo("cliente@test.cl");
            assertThat(p.clienteNombre()).isEqualTo("Cliente");
        });
    }

    @Test
    void miPedido_deOtroCliente_404SinRevelarQueExiste() {
        web(2, "PP-2026-000002", 30, EstadoPedido.PAGADO);
        assertThatThrownBy(() -> service.delCliente(10, "PP-2026-000002")).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void misPedidos_paginaInvalida_validation() {
        assertThatThrownBy(() -> service.delCliente(10, 0, 0)).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> service.delCliente(10, -1, 10)).isInstanceOf(ValidacionException.class);
    }

    @Test
    void marcarRetiro_cambiaEstadoYActualizaFecha() {
        web(1, "PP-2026-000001", 10, EstadoPedido.PAGADO);
        var listo = service.marcarRetiro("pp-2026-000001", EstadoPedido.LISTO_RETIRO);
        assertThat(listo.estado()).isEqualTo(EstadoPedido.LISTO_RETIRO);
        assertThat(listo.actualizadoEn()).isEqualTo(AHORA);
    }

    @Test
    void marcarRetiro_estadoNoDeRetiro_validation() {
        web(1, "PP-2026-000001", 10, EstadoPedido.PAGADO);
        assertThatThrownBy(() -> service.marcarRetiro("PP-2026-000001", EstadoPedido.REEMBOLSADO))
                .isInstanceOf(ValidacionException.class);
    }

    @Test
    void marcarRetiro_codigoInexistente_404() {
        assertThatThrownBy(() -> service.marcarRetiro("PP-2026-999999", EstadoPedido.ENTREGADO))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void marcarRetiro_siOtroProcesoLoCambioAntes_orderInvalidState() {
        web(1, "PP-2026-000001", 10, EstadoPedido.PAGADO);
        repo.otroProcesoGana = true;
        assertThatThrownBy(() -> service.marcarRetiro("PP-2026-000001", EstadoPedido.ENTREGADO))
                .isInstanceOf(EstadoPedidoInvalidoException.class);
    }
}
