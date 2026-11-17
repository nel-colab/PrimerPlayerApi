package cl.primerplayer.ventas.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.catalogo.api.ConsultarCatalogoPublicoUseCase;
import cl.primerplayer.catalogo.api.ProductoPublico;
import cl.primerplayer.eventos.api.ConsultarEventosUseCase;
import cl.primerplayer.eventos.api.CuposOperaciones.LineaCupo;
import cl.primerplayer.eventos.api.EventoVista;
import cl.primerplayer.eventos.api.InscripcionesOperaciones;
import cl.primerplayer.identidad.api.ConsultarUsuarioUseCase;
import cl.primerplayer.inventario.api.InventarioOperaciones;
import cl.primerplayer.inventario.api.InventarioOperaciones.LineaStock;
import cl.primerplayer.inventario.api.TipoMovimiento;
import cl.primerplayer.shared.api.ExcepcionNegocio;
import cl.primerplayer.shared.api.ExcepcionNegocio.ErrorLinea;
import cl.primerplayer.shared.api.FechasTienda;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import cl.primerplayer.shared.api.ValidacionException;
import cl.primerplayer.ventas.api.CanalPedido;
import cl.primerplayer.ventas.api.CarritoInvalidoException;
import cl.primerplayer.ventas.api.ConsultarPedidosUseCase;
import cl.primerplayer.ventas.api.CorreoNoVerificadoException;
import cl.primerplayer.ventas.api.EstadoPedido;
import cl.primerplayer.ventas.api.EstadoPedidoInvalidoException;
import cl.primerplayer.ventas.api.IdempotenciaConflictoException;
import cl.primerplayer.ventas.api.PedidoVista;
import cl.primerplayer.ventas.api.VentaMostradorUseCase;
import cl.primerplayer.ventas.api.VentaWebUseCase;
import cl.primerplayer.ventas.application.port.out.PedidoRepositorio;
import cl.primerplayer.ventas.application.port.out.VentaWebRepositorio;
import cl.primerplayer.ventas.domain.Pedido;
import cl.primerplayer.ventas.application.port.out.VentaWebRepositorio.ItemNuevo;
import cl.primerplayer.ventas.application.port.out.VentaWebRepositorio.NuevoPedido;
import cl.primerplayer.ventas.application.port.out.VentaWebRepositorio.PedidoIdempotente;

@Service
class VentaWebService implements VentaWebUseCase, VentaMostradorUseCase {

    private static final Set<EstadoPedido> DESTINOS_SIN_PAGO =
            Set.of(EstadoPedido.RECHAZADO, EstadoPedido.ANULADO, EstadoPedido.EXPIRADO);

    private final VentaWebRepositorio pedidos;
    private final PedidoRepositorio pedidosLectura;
    private final ConsultarPedidosUseCase consultas;
    private final ConsultarCatalogoPublicoUseCase catalogo;
    private final ConsultarEventosUseCase eventos;
    private final InscripcionesOperaciones inscripciones;
    private final InventarioOperaciones inventario;
    private final ConsultarUsuarioUseCase usuarios;
    private final Clock clock;
    private final Duration reserva;
    private final org.springframework.context.ApplicationEventPublisher eventosPublicados;

    VentaWebService(VentaWebRepositorio pedidos, PedidoRepositorio pedidosLectura, ConsultarPedidosUseCase consultas,
                    ConsultarCatalogoPublicoUseCase catalogo, ConsultarEventosUseCase eventos,
                    InscripcionesOperaciones inscripciones, InventarioOperaciones inventario,
                    ConsultarUsuarioUseCase usuarios, Clock clock,
                    org.springframework.context.ApplicationEventPublisher eventosPublicados,
                    @Value("${pp.checkout.reserva-minutos:15}") long reservaMinutos) {
        this.eventosPublicados = eventosPublicados;
        this.pedidos = pedidos;
        this.pedidosLectura = pedidosLectura;
        this.consultas = consultas;
        this.catalogo = catalogo;
        this.eventos = eventos;
        this.inscripciones = inscripciones;
        this.inventario = inventario;
        this.usuarios = usuarios;
        this.clock = clock;
        this.reserva = Duration.ofMinutes(reservaMinutos);
    }

    // ---------- venta de mostrador ----------

    @Override
    @Transactional(readOnly = true)
    public ValidacionCarrito validar(List<LineaCarrito> lineas) {
        validarForma(lineas);
        return evaluar(null, lineas, clock.instant());   // sin límite por cliente (cliente anónimo)
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)   // la abre el POS, junto con los pagos
    public PedidoCreado crear(long vendedorId, List<LineaCarrito> lineas, UUID idempotencyKey, String hashCuerpo) {
        validarForma(lineas);
        long mostrador = usuarios.clienteMostradorId();
        pedidos.bloquearCliente(mostrador);   // dos envíos con la misma clave no crean dos ventas

        Optional<PedidoIdempotente> previo = pedidos.porIdempotencia(mostrador, idempotencyKey);
        if (previo.isPresent()) {
            PedidoIdempotente p = previo.get();
            if (!p.hash().equals(hashCuerpo)) {
                throw new IdempotenciaConflictoException();
            }
            return new PedidoCreado(p.id(), p.codigo(), p.estado(), p.total(), null, true);
        }

        Instant ahora = clock.instant();
        ValidacionCarrito v = evaluar(null, lineas, ahora);
        if (!v.valido()) {
            throw invalido(v.errores());
        }
        String codigo = pedidos.siguienteCodigo(ahora.atZone(FechasTienda.ZONA).getYear());
        // Los productos se llevan en el momento; si solo hay cupos de eventos, la venta queda pagada.
        EstadoPedido estado = lineas.stream().anyMatch(LineaCarrito::esProducto) ? EstadoPedido.ENTREGADO : EstadoPedido.PAGADO;
        List<ItemNuevo> items = v.lineas().stream()
                .map(l -> new ItemNuevo(l.tipo(), l.itemId(), l.nombre(), l.cantidad(), l.precioUnitario())).toList();
        long pedidoId = pedidos.insertar(new NuevoPedido(codigo, mostrador, vendedorId, "tienda", estado, v.total(),
                idempotencyKey, hashCuerpo, null, ahora, ahora, items));

        descontarYReservar(mostrador, pedidoId, codigo, lineas, TipoMovimiento.VENTA_MOSTRADOR);
        inscripciones.confirmar(pedidoId);
        return new PedidoCreado(pedidoId, codigo, estado, v.total(), null, false);
    }

    @Override
    @Transactional
    public PedidoVista anular(String codigo) {
        var p = pedidosLectura.porCodigo(codigo)
                .orElseThrow(() -> new RecursoNoEncontradoException("La venta no existe."));
        if (p.canal() != CanalPedido.TIENDA) {
            throw new EstadoPedidoInvalidoException("Solo se anulan ventas de mostrador; los pedidos web se reembolsan.");
        }
        if (!Pedido.puedePasar(p.estado(), EstadoPedido.ANULADO)
                || !pedidosLectura.cambiarEstado(p.id(), p.estado(), EstadoPedido.ANULADO, clock.instant())) {
            throw new EstadoPedidoInvalidoException("La venta %s está %s y no se puede anular.".formatted(codigo, p.estado()));
        }
        var productos = p.items().stream().filter(Pedido.Item::esProducto).toList();
        if (!productos.isEmpty()) {
            inventario.reponer(productos.stream().map(i -> new LineaStock(i.itemId(), i.cantidad())).toList(),
                    TipoMovimiento.ANULACION, p.codigo());
        }
        inscripciones.liberar(p.id(), false);
        return consultas.porCodigo(p.codigo());
    }

    /** Descuento atómico de stock y cupos; los errores se traducen al índice de línea del carrito. */
    private void descontarYReservar(long clienteId, long pedidoId, String codigo, List<LineaCarrito> lineas,
                                    TipoMovimiento tipo) {
        List<Integer> idxProductos = indices(lineas, true);
        if (!idxProductos.isEmpty()) {
            try {
                inventario.descontar(idxProductos.stream().map(i -> new LineaStock(lineas.get(i).itemId(),
                        lineas.get(i).cantidad())).toList(), tipo, codigo);
            } catch (ExcepcionNegocio e) {
                throw reindexar(e, idxProductos);
            }
        }
        List<Integer> idxEventos = indices(lineas, false);
        if (!idxEventos.isEmpty()) {
            try {
                inscripciones.inscribir(clienteId, pedidoId, idxEventos.stream().map(i -> new LineaCupo(
                        lineas.get(i).itemId(), lineas.get(i).cantidad())).toList());
            } catch (ExcepcionNegocio e) {
                throw reindexar(e, idxEventos);
            }
        }
    }

    // ---------- validación (sin reservar) ----------

    @Override
    @Transactional(readOnly = true)
    public ValidacionCarrito validar(Long clienteId, List<LineaCarrito> lineas) {
        validarForma(lineas);
        return evaluar(clienteId, lineas, clock.instant());
    }

    // ---------- checkout ----------

    @Override
    @Transactional(propagation = Propagation.MANDATORY)   // la abre checkout; Webpay va fuera
    public PedidoCreado crearPendiente(long clienteId, List<LineaCarrito> lineas, UUID idempotencyKey, String hashCuerpo) {
        validarForma(lineas);
        switch (usuarios.estadoParaComprar(clienteId)) {
            case PERMITIDO -> { }
            case LISTA_NEGRA -> throw new cl.primerplayer.identidad.api.ListaNegraException();
            case NO_DISPONIBLE -> throw new cl.primerplayer.identidad.api.SesionInvalidaException();
            case SIN_VERIFICAR -> throw new CorreoNoVerificadoException();
        }
        pedidos.bloquearCliente(clienteId);   // dos pestañas del mismo cliente no se saltan su límite

        Optional<PedidoIdempotente> previo = pedidos.porIdempotencia(clienteId, idempotencyKey);
        if (previo.isPresent()) {
            PedidoIdempotente p = previo.get();
            if (!p.hash().equals(hashCuerpo)) {
                throw new IdempotenciaConflictoException();
            }
            return new PedidoCreado(p.id(), p.codigo(), p.estado(), p.total(), p.expiraEn(), true);
        }

        Instant ahora = clock.instant();
        ValidacionCarrito v = evaluar(clienteId, lineas, ahora);
        if (!v.valido()) {
            throw invalido(v.errores());
        }

        String codigo = pedidos.siguienteCodigo(ahora.atZone(FechasTienda.ZONA).getYear());
        Instant expiraEn = ahora.plus(reserva);
        List<ItemNuevo> items = v.lineas().stream()
                .map(l -> new ItemNuevo(l.tipo(), l.itemId(), l.nombre(), l.cantidad(), l.precioUnitario())).toList();
        long pedidoId = pedidos.insertar(new NuevoPedido(codigo, clienteId, null, "web", EstadoPedido.PENDIENTE_PAGO,
                v.total(), idempotencyKey, hashCuerpo, expiraEn, null, ahora, items));

        // Descuento atómico (UPDATE ... WHERE stock >= q). La validación previa da buenos mensajes,
        // pero la verdad la tiene este UPDATE: si otro cliente se llevó la última unidad, falla aquí y se deshace todo.
        descontarYReservar(clienteId, pedidoId, codigo, lineas, TipoMovimiento.VENTA_WEB);

        if (v.total() == 0) {   // solo eventos gratuitos: se confirma sin pasar por Webpay
            pedidos.marcarPagado(pedidoId, ahora);
            inscripciones.confirmar(pedidoId);
            eventosPublicados.publishEvent(new cl.primerplayer.ventas.events.PedidoConfirmado(pedidoId, codigo, clienteId, 0));
            return new PedidoCreado(pedidoId, codigo, EstadoPedido.PAGADO, 0, null, false);
        }
        return new PedidoCreado(pedidoId, codigo, EstadoPedido.PENDIENTE_PAGO, v.total(), expiraEn, false);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean confirmarPago(long pedidoId) {
        if (!pedidos.marcarPagado(pedidoId, clock.instant())) {
            return false;
        }
        inscripciones.confirmar(pedidoId);
        var resumen = pedidosLectura.porId(pedidoId);
        resumen.ifPresent(p -> eventosPublicados.publishEvent(
                new cl.primerplayer.ventas.events.PedidoConfirmado(p.id(), p.codigo(), p.clienteId(), p.total())));
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean cerrarSinPago(long pedidoId, EstadoPedido destino) {
        if (!DESTINOS_SIN_PAGO.contains(destino)) {
            throw new IllegalArgumentException("Destino sin pago inválido: " + destino);
        }
        if (!pedidos.cerrarPendiente(pedidoId, destino, clock.instant())) {
            return false;   // ya resuelto por otro proceso: no se repone dos veces
        }
        boolean vencido = destino == EstadoPedido.EXPIRADO;
        var productos = pedidos.productosDe(pedidoId);
        if (!productos.isEmpty()) {
            inventario.reponer(productos.stream().map(p -> new LineaStock(p.productoId(), p.cantidad())).toList(),
                    vencido ? TipoMovimiento.RESERVA_EXPIRADA : TipoMovimiento.ANULACION, pedidos.codigoDe(pedidoId));
        }
        inscripciones.liberar(pedidoId, vencido);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> vencidos(int limite) {
        return pedidos.vencidos(clock.instant(), limite);
    }

    // ---------- reglas del carrito ----------

    private static void validarForma(List<LineaCarrito> lineas) {
        if (lineas == null || lineas.isEmpty()) {
            throw CarritoInvalidoException.vacio();
        }
        if (lineas.size() > MAX_LINEAS) {
            throw new ValidacionException("lineas", "Máximo " + MAX_LINEAS + " líneas por pedido.");
        }
        Set<String> vistos = new HashSet<>();
        for (LineaCarrito l : lineas) {
            if (!"product".equals(l.tipo()) && !"event".equals(l.tipo())) {
                throw new ValidacionException("lineas", "type debe ser product o event.");
            }
            if (!vistos.add(l.tipo() + ":" + l.itemId())) {
                throw new ValidacionException("lineas", "Hay ítems repetidos: agrúpalos en una sola línea.");
            }
        }
    }

    /** Evalúa cada línea con precios y disponibilidad actuales; los problemas se acumulan en {@code errores}. */
    private ValidacionCarrito evaluar(Long clienteId, List<LineaCarrito> lineas, Instant ahora) {
        Map<Long, ProductoPublico> productos = catalogo.visibles(
                lineas.stream().filter(LineaCarrito::esProducto).map(LineaCarrito::itemId).toList());
        List<LineaValidada> validadas = new ArrayList<>();
        List<ErrorLinea> errores = new ArrayList<>();
        long total = 0;

        for (int i = 0; i < lineas.size(); i++) {
            LineaCarrito l = lineas.get(i);
            boolean cantidadOk = l.cantidad() >= 1 && l.cantidad() <= MAX_CANTIDAD;
            if (!cantidadOk) {
                errores.add(new ErrorLinea(i, l.tipo(), l.itemId(), "invalid-quantity", null));
            }

            String nombre = null;
            long precio = 0;
            Integer disponible = null;
            Integer maxPermitido = null;
            String error = null;

            if (l.esProducto()) {
                ProductoPublico p = productos.get(l.itemId());
                if (p == null) {
                    error = "item-unavailable";
                } else {
                    nombre = p.nombre();
                    precio = p.precio();
                    disponible = p.stock();
                    maxPermitido = restante(p.limitePorCliente(), clienteId == null ? 0
                            : pedidos.cantidadComprada(clienteId, p.id()), clienteId);
                    if (p.stock() == 0) {
                        error = "out-of-stock";
                    } else if (l.cantidad() > p.stock()) {
                        error = "stock-limit";
                    }
                }
            } else {
                EventoVista e = eventoVisible(l.itemId());
                if (e == null || e.cuposTotales() == null) {   // cumpleaños: no vende cupos
                    error = "item-unavailable";
                } else {
                    nombre = e.titulo();
                    precio = e.precio();
                    disponible = e.cuposDisponibles();
                    maxPermitido = restante(e.limitePorCliente(), clienteId == null ? 0
                            : inscripciones.cuposVigentes(clienteId, e.id()), clienteId);
                    if (!e.inicio().isAfter(ahora)) {
                        error = "event-past";
                    } else if (l.cantidad() > e.cuposDisponibles()) {
                        error = "event-full";
                    }
                }
            }
            if (error == null && maxPermitido != null && l.cantidad() > maxPermitido) {
                error = "purchase-limit";
            }
            if (error != null && cantidadOk) {
                errores.add(new ErrorLinea(i, l.tipo(), l.itemId(), error,
                        "purchase-limit".equals(error) ? maxPermitido : null));
            }
            long subtotal = cantidadOk ? precio * l.cantidad() : 0;
            total += subtotal;
            validadas.add(new LineaValidada(l.tipo(), l.itemId(), nombre, l.cantidad(), precio, subtotal,
                    disponible, maxPermitido));
        }
        return new ValidacionCarrito(validadas, total, errores);
    }

    /** Unidades que aún puede comprar; {@code null} = sin límite o visitante (el límite se evalúa al comprar). */
    private static Integer restante(Integer limite, int yaComprado, Long clienteId) {
        if (limite == null || clienteId == null) {
            return null;
        }
        return Math.max(0, limite - yaComprado);
    }

    private EventoVista eventoVisible(long id) {
        try {
            return eventos.obtenerPublico(id);
        } catch (RecursoNoEncontradoException e) {
            return null;
        }
    }

    private static List<Integer> indices(List<LineaCarrito> lineas, boolean productos) {
        List<Integer> r = new ArrayList<>();
        for (int i = 0; i < lineas.size(); i++) {
            if (lineas.get(i).esProducto() == productos) {
                r.add(i);
            }
        }
        return r;
    }

    private static CarritoInvalidoException invalido(List<ErrorLinea> errores) {
        String code = errores.getFirst().code();
        return new CarritoInvalidoException(code, mensaje(code), errores);
    }

    /** El inventario y los cupos numeran las líneas dentro de su sublista: se traducen al índice del carrito. */
    private static CarritoInvalidoException reindexar(ExcepcionNegocio e, List<Integer> indices) {
        List<ErrorLinea> errores = e.errores().stream()
                .map(x -> new ErrorLinea(x.line() >= 0 && x.line() < indices.size() ? indices.get(x.line()) : x.line(),
                        x.itemType(), x.itemId(), x.code(), x.maxAllowed()))
                .toList();
        String code = e instanceof RecursoNoEncontradoException ? "item-unavailable" : e.code();
        return new CarritoInvalidoException(code, e.getMessage(), errores);
    }

    private static String mensaje(String code) {
        return switch (code) {
            case "invalid-quantity" -> "Hay cantidades inválidas (deben estar entre 1 y " + MAX_CANTIDAD + ").";
            case "item-unavailable" -> "Hay productos o eventos que ya no están disponibles.";
            case "out-of-stock" -> "Hay productos sin stock.";
            case "stock-limit" -> "No hay stock suficiente para algunas cantidades.";
            case "event-full" -> "No quedan cupos suficientes en algún evento.";
            case "event-past" -> "Algún evento del carrito ya ocurrió.";
            case "purchase-limit" -> "Superas el límite de compra por cliente en algún ítem.";
            default -> "El carrito tiene problemas.";
        };
    }
}
