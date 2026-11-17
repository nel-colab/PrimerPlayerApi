package cl.primerplayer.caja.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.caja.api.CajaUseCase;
import cl.primerplayer.caja.application.port.out.CierreRepositorio;
import cl.primerplayer.caja.application.port.out.CierreRepositorio.Fila;
import cl.primerplayer.identidad.api.ConsultarUsuarioUseCase;
import cl.primerplayer.identidad.api.UsuarioResumen;
import cl.primerplayer.shared.api.FechasTienda;
import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.ValidacionException;
import cl.primerplayer.ventas.api.CanalPedido;
import cl.primerplayer.ventas.api.ConsultarPedidosUseCase;
import cl.primerplayer.ventas.api.PedidoVista;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class CajaService implements CajaUseCase {

    static final int MAX_OBSERVACIONES = 1000;
    static final int MAX_PAGE_SIZE = 100;

    private final ConsultarPedidosUseCase pedidos;
    private final CierreRepositorio cierres;
    private final ConsultarUsuarioUseCase usuarios;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public ResumenDia resumen(LocalDate fecha) {
        return calcular(fecha, cierres.porFecha(fecha, false).map(this::aCierre).orElse(null));
    }

    @Override
    @Transactional
    public Cierre cerrar(LocalDate fecha, long fondoInicial, long efectivoContado, String observaciones, long usuarioId) {
        if (fecha.isAfter(LocalDate.now(clock.withZone(FechasTienda.ZONA)))) {
            throw new ValidacionException("fecha", "No se puede cerrar la caja de un día futuro.");
        }
        if (fondoInicial < 0) {
            throw new ValidacionException("fondoInicial", "No puede ser negativo.");
        }
        if (efectivoContado < 0) {
            throw new ValidacionException("efectivoContado", "No puede ser negativo.");
        }
        String obs = observaciones == null ? "" : observaciones.strip();
        if (obs.length() > MAX_OBSERVACIONES) {
            throw new ValidacionException("observaciones", "Máximo " + MAX_OBSERVACIONES + " caracteres.");
        }

        ResumenDia r = calcular(fecha, null);
        long esperado = fondoInicial + r.efectivoNeto();
        Instant ahora = clock.instant();
        Fila nueva = new Fila(fecha, fondoInicial, efectivoContado, r.efectivoNeto(), esperado, r.total(),
                r.numeroVentas(), efectivoContado - esperado, r.subtotales(), obs, 1, usuarioId, ahora);

        if (!cierres.insertar(nueva)) {   // ya estaba cerrada: se reemplaza guardando la versión anterior
            Fila anterior = cierres.porFecha(fecha, true).orElseThrow();
            cierres.reemplazar(anterior, new Fila(fecha, fondoInicial, efectivoContado, r.efectivoNeto(), esperado,
                    r.total(), r.numeroVentas(), efectivoContado - esperado, r.subtotales(), obs,
                    anterior.version() + 1, usuarioId, ahora));
        }
        return aCierre(cierres.porFecha(fecha, false).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<Cierre> cierres(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ValidacionException("size", "page >= 0 y size entre 1 y " + MAX_PAGE_SIZE + ".");
        }
        List<Fila> filas = cierres.pagina(page, size);
        Map<Long, UsuarioResumen> personas = usuarios.buscarPorIds(filas.stream().map(Fila::cerradoPor).distinct().toList());
        return Pagina.de(filas.stream().map(f -> aCierre(f, personas)).toList(), page, size, cierres.contar());
    }

    // ---------- cálculo del día ----------

    private ResumenDia calcular(LocalDate fecha, Cierre cierre) {
        Instant desde = fecha.atStartOfDay(FechasTienda.ZONA).toInstant();
        Instant hasta = fecha.plusDays(1).atStartOfDay(FechasTienda.ZONA).toInstant();
        List<PedidoVista> ventas = pedidos.validasEntre(desde, hasta);

        long total = 0;
        long totalTienda = 0;
        long totalWeb = 0;
        int unidades = 0;
        int pagosEfectivo = 0;
        long efectivoNeto = 0;
        Map<String, Long> subtotales = new TreeMap<>();
        Map<String, ProductoVendido> productos = new LinkedHashMap<>();

        for (PedidoVista p : ventas) {
            total += p.total();
            if (p.canal() == CanalPedido.TIENDA) {
                totalTienda += p.total();
            } else {
                totalWeb += p.total();
            }
            for (var pago : p.pagos()) {
                subtotales.merge(pago.metodo(), pago.monto(), Long::sum);
                if ("efectivo".equals(pago.metodo()) && p.canal() == CanalPedido.TIENDA) {
                    pagosEfectivo++;
                    efectivoNeto += pago.efectivoRecibido() - pago.vueltoEntregado();
                }
            }
            for (var i : p.items()) {
                unidades += i.cantidad();
                productos.merge(i.tipo() + ":" + i.itemId(),
                        new ProductoVendido(i.tipo(), i.itemId(), i.nombre(), i.cantidad(), i.subtotal()),
                        (a, b) -> new ProductoVendido(a.tipo(), a.itemId(), a.nombre(), a.unidades() + b.unidades(),
                                a.monto() + b.monto()));
            }
        }
        List<ProductoVendido> ordenados = productos.values().stream()
                .sorted(Comparator.comparingInt(ProductoVendido::unidades).reversed()
                        .thenComparing(ProductoVendido::nombre))
                .toList();
        return new ResumenDia(fecha, ventas.size(), unidades, total, totalTienda, totalWeb, subtotales, pagosEfectivo,
                efectivoNeto, ordenados, cierre);
    }

    // ---------- mapeo ----------

    private Cierre aCierre(Fila f) {
        return aCierre(f, usuarios.buscarPorIds(List.of(f.cerradoPor())));
    }

    private static Cierre aCierre(Fila f, Map<Long, UsuarioResumen> personas) {
        UsuarioResumen quien = personas.get(f.cerradoPor());
        String estado = f.diferencia() == 0 ? "cuadrada" : f.diferencia() > 0 ? "sobrante" : "faltante";
        return new Cierre(f.fecha(), f.fondoInicial(), f.efectivoContado(), f.efectivoNeto(), f.esperadoEnCaja(),
                f.diferencia(), estado, f.totalVentas(), f.numeroVentas(), f.subtotales(), f.observaciones(),
                f.version(), f.cerradoPor(), quien == null ? null : quien.nombre(), f.cerradoEn());
    }
}
