package cl.primerplayer.ventas.application;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.eventos.api.ConsultarInscripcionesUseCase;
import cl.primerplayer.eventos.api.ConsultarInscripcionesUseCase.EstadoInscripcion;
import cl.primerplayer.eventos.api.ConsultarInscripcionesUseCase.InscripcionVista;
import cl.primerplayer.identidad.api.ConsultarUsuarioUseCase;
import cl.primerplayer.identidad.api.UsuarioResumen;
import cl.primerplayer.ventas.api.CanalPedido;
import cl.primerplayer.ventas.api.InscripcionesVentaUseCase;
import cl.primerplayer.ventas.application.port.out.VentaWebRepositorio;
import cl.primerplayer.ventas.application.port.out.VentaWebRepositorio.ResumenPedido;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class InscripcionesVentaService implements InscripcionesVentaUseCase {

    private final ConsultarInscripcionesUseCase inscripciones;
    private final VentaWebRepositorio pedidos;
    private final ConsultarUsuarioUseCase usuarios;

    @Override
    @Transactional(readOnly = true)
    public List<InscripcionCliente> delCliente(long clienteId) {
        List<InscripcionVista> lista = inscripciones.delCliente(clienteId);
        Map<Long, ResumenPedido> resumenes = resumenes(lista);
        return lista.stream().map(i -> new InscripcionCliente(i, codigo(resumenes, i))).toList();
    }

    @Override
    @Transactional
    public InscripcionCliente cancelar(long clienteId, long inscripcionId) {
        InscripcionVista i = inscripciones.cancelarPorCliente(clienteId, inscripcionId);
        return new InscripcionCliente(i, pedidos.codigoDe(i.pedidoId()));
    }

    @Override
    @Transactional(readOnly = true)
    public InscritosEvento inscritos(long eventoId) {
        List<InscripcionVista> lista = inscripciones.delEvento(eventoId);
        Map<Long, ResumenPedido> resumenes = resumenes(lista);
        Map<Long, UsuarioResumen> clientes = usuarios.buscarPorIds(
                lista.stream().map(InscripcionVista::clienteId).distinct().toList());

        List<Inscrito> inscritos = lista.stream().map(i -> {
            ResumenPedido p = resumenes.get(i.pedidoId());
            UsuarioResumen c = clientes.get(i.clienteId());
            return new Inscrito(i, p == null ? null : p.codigo(), p == null ? null : CanalPedido.de(p.canal()),
                    c == null ? null : c.nombre(), c == null ? null : c.email());
        }).toList();
        return new InscritosEvento(eventoId, cupos(lista, EstadoInscripcion.CONFIRMADA),
                cupos(lista, EstadoInscripcion.RESERVADA), inscritos);
    }

    private Map<Long, ResumenPedido> resumenes(List<InscripcionVista> lista) {
        return pedidos.resumenes(lista.stream().map(InscripcionVista::pedidoId).distinct().toList());
    }

    private static String codigo(Map<Long, ResumenPedido> resumenes, InscripcionVista i) {
        ResumenPedido p = resumenes.get(i.pedidoId());
        return p == null ? null : p.codigo();
    }

    private static int cupos(List<InscripcionVista> lista, EstadoInscripcion estado) {
        return lista.stream().filter(i -> i.estado() == estado).mapToInt(InscripcionVista::cantidad).sum();
    }
}
