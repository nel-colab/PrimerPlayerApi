package cl.primerplayer.ventas.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.identidad.api.ConsultarUsuarioUseCase;
import cl.primerplayer.identidad.api.UsuarioResumen;
import cl.primerplayer.pagos.api.ConsultarPagosUseCase;
import cl.primerplayer.pagos.api.ConsultarPagosUseCase.PagoVista;
import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import cl.primerplayer.shared.api.ValidacionException;
import cl.primerplayer.ventas.api.ConsultarPedidosUseCase;
import cl.primerplayer.ventas.api.EstadoPedido;
import cl.primerplayer.ventas.api.EstadoPedidoInvalidoException;
import cl.primerplayer.ventas.api.GestionarPedidosUseCase;
import cl.primerplayer.ventas.api.PedidoVista;
import cl.primerplayer.ventas.application.port.out.PedidoRepositorio;
import cl.primerplayer.ventas.domain.Pedido;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class PedidosService implements ConsultarPedidosUseCase, GestionarPedidosUseCase {

    private final PedidoRepositorio pedidos;
    private final ConsultarUsuarioUseCase usuarios;
    private final ConsultarPagosUseCase pagos;
    private final Clock clock;

    // ---------- lecturas ----------

    @Override
    @Transactional(readOnly = true)
    public Pagina<PedidoVista> delCliente(long clienteId, int page, int size) {
        ConsultarPedidosUseCase.validarPagina(page, size);
        return aVistas(pedidos.delCliente(clienteId, page, size));
    }

    @Override
    @Transactional(readOnly = true)
    public PedidoVista delCliente(long clienteId, String codigo) {
        Pedido p = pedidos.porCodigo(codigo).filter(x -> x.clienteId() == clienteId)
                .orElseThrow(PedidosService::noEncontrado);   // de otro cliente: 404, no revela que existe
        return aVista(p);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<PedidoVista> buscar(FiltroPedidos filtro) {
        return aVistas(pedidos.buscar(filtro));
    }

    @Override
    @Transactional(readOnly = true)
    public PedidoVista porCodigo(String codigo) {
        return aVista(pedidos.porCodigo(codigo).orElseThrow(PedidosService::noEncontrado));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PedidoVista> validasEntre(java.time.Instant desde, java.time.Instant hasta) {
        List<Pedido> lista = pedidos.validosEntre(desde, hasta);
        Map<Long, List<PagoVista>> pagosPorPedido = pagos.dePedidos(lista.stream().map(Pedido::id).toList());
        return lista.stream().map(p -> aVista(p, Map.of(), pagosPorPedido)).toList();
    }

    // ---------- comandos ----------

    @Override
    @Transactional
    public PedidoVista marcarRetiro(String codigo, EstadoPedido nuevo) {
        if (!Pedido.ESTADOS_DE_RETIRO.contains(nuevo)) {
            throw new ValidacionException("estado", "Use LISTO_RETIRO o ENTREGADO.");
        }
        Pedido actual = pedidos.porCodigo(codigo).orElseThrow(PedidosService::noEncontrado);
        actual.validarRetiro(nuevo);
        if (!pedidos.cambiarEstado(actual.id(), actual.estado(), nuevo, clock.instant())) {
            throw new EstadoPedidoInvalidoException("El pedido cambió de estado mientras tanto; recarga e intenta de nuevo.");
        }
        return aVista(pedidos.porCodigo(codigo).orElseThrow());
    }

    // ---------- mapeo con datos de identidad y pagos ----------

    private Pagina<PedidoVista> aVistas(Pagina<Pedido> pagina) {
        Map<Long, UsuarioResumen> personas = personas(pagina.content());
        Map<Long, List<PagoVista>> pagosPorPedido = pagos.dePedidos(pagina.content().stream().map(Pedido::id).toList());
        return pagina.map(p -> aVista(p, personas, pagosPorPedido));
    }

    private PedidoVista aVista(Pedido p) {
        return aVista(p, personas(List.of(p)), pagos.dePedidos(List.of(p.id())));
    }

    /** Clientes y vendedores de todos los pedidos en una sola consulta. */
    private Map<Long, UsuarioResumen> personas(List<Pedido> lista) {
        List<Long> ids = lista.stream()
                .flatMap(p -> Stream.of(p.clienteId(), p.vendedorId()))
                .filter(Objects::nonNull).distinct().toList();
        return usuarios.buscarPorIds(ids);
    }

    private static PedidoVista aVista(Pedido p, Map<Long, UsuarioResumen> personas, Map<Long, List<PagoVista>> pagos) {
        UsuarioResumen cliente = personas.get(p.clienteId());
        UsuarioResumen vendedor = p.vendedorId() == null ? null : personas.get(p.vendedorId());
        return new PedidoVista(p.id(), p.codigo(), p.canal(), p.estado(), p.total(), p.clienteId(),
                cliente == null ? null : cliente.email(), cliente == null ? null : cliente.nombre(),
                p.vendedorId(), vendedor == null ? null : vendedor.nombre(),
                p.creadoEn(), p.pagadoEn(), p.actualizadoEn(),
                p.items().stream().map(i -> new PedidoVista.Item(i.tipo(), i.itemId(), i.nombre(), i.cantidad(),
                        i.precioUnitario(), i.subtotal())).toList(),
                pagos.getOrDefault(p.id(), List.of()));
    }

    private static RecursoNoEncontradoException noEncontrado() {
        return new RecursoNoEncontradoException("El pedido no existe.");
    }
}
