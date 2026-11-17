package cl.primerplayer.reportes.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.ToIntFunction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.reportes.api.ReportesUseCase;
import cl.primerplayer.shared.api.FechasTienda;
import cl.primerplayer.shared.api.ValidacionException;
import cl.primerplayer.ventas.api.CanalPedido;
import cl.primerplayer.ventas.api.ConsultarPedidosUseCase;
import cl.primerplayer.ventas.api.PedidoVista;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class ReportesService implements ReportesUseCase {

    private static final int TOP = 5;

    private final ConsultarPedidosUseCase pedidos;

    @Override
    @Transactional(readOnly = true)
    public ReporteDia dia(LocalDate fecha) {
        List<PedidoVista> hoy = ventas(fecha, fecha.plusDays(1));
        long anterior = ventas(fecha.minusDays(1), fecha).stream().mapToLong(PedidoVista::total).sum();
        long total = total(hoy);
        long productos = 0;
        long eventos = 0;
        long tienda = 0;
        for (PedidoVista p : hoy) {
            for (var i : p.items()) {
                if ("product".equals(i.tipo())) {
                    productos += i.subtotal();
                } else {
                    eventos += i.subtotal();
                }
            }
            if (p.canal() == CanalPedido.TIENDA) {
                tienda += p.total();
            }
        }
        Double variacion = anterior == 0 ? null : BigDecimal.valueOf((total - anterior) * 100.0 / anterior)
                .setScale(1, RoundingMode.HALF_UP).doubleValue();
        return new ReporteDia(fecha, total, anterior, variacion, hoy.size(), promedio(hoy), productos, eventos, tienda,
                total - tienda, porMetodo(hoy), top(hoy));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Tramo> porHora(LocalDate fecha) {
        return tramos(ventas(fecha, fecha.plusDays(1)), 0, 23, z -> z.getHour());
    }

    @Override
    @Transactional(readOnly = true)
    public ReportePeriodo porDia(YearMonth mes) {
        List<PedidoVista> lista = ventas(mes.atDay(1), mes.plusMonths(1).atDay(1));
        return periodo(mes.toString(), lista, tramos(lista, 1, mes.lengthOfMonth(), ZonedDateTime::getDayOfMonth));
    }

    @Override
    @Transactional(readOnly = true)
    public ReportePeriodo porMes(int anio) {
        if (anio < 2000 || anio > 2100) {
            throw new ValidacionException("anio", "Año fuera de rango.");
        }
        List<PedidoVista> lista = ventas(LocalDate.of(anio, 1, 1), LocalDate.of(anio + 1, 1, 1));
        return periodo(String.valueOf(anio), lista, tramos(lista, 1, 12, ZonedDateTime::getMonthValue));
    }

    // ---------- cálculo ----------

    /** Ventas válidas de {@code [desde, hasta)} en días de Chile. */
    private List<PedidoVista> ventas(LocalDate desde, LocalDate hasta) {
        Instant d = desde.atStartOfDay(FechasTienda.ZONA).toInstant();
        Instant h = hasta.atStartOfDay(FechasTienda.ZONA).toInstant();
        return pedidos.validasEntre(d, h);
    }

    private static ReportePeriodo periodo(String nombre, List<PedidoVista> lista, List<Tramo> tramos) {
        return new ReportePeriodo(nombre, total(lista), lista.size(), promedio(lista), tramos, porMetodo(lista), top(lista));
    }

    private static List<Tramo> tramos(List<PedidoVista> lista, int desde, int hasta, ToIntFunction<ZonedDateTime> clave) {
        Map<Integer, long[]> acumulado = new TreeMap<>();
        for (int t = desde; t <= hasta; t++) {
            acumulado.put(t, new long[2]);
        }
        for (PedidoVista p : lista) {
            long[] a = acumulado.get(clave.applyAsInt(p.creadoEn().atZone(FechasTienda.ZONA)));
            if (a != null) {
                a[0]++;
                a[1] += p.total();
            }
        }
        List<Tramo> r = new ArrayList<>();
        acumulado.forEach((t, a) -> r.add(new Tramo(t, (int) a[0], a[1])));
        return r;
    }

    private static List<PorMetodo> porMetodo(List<PedidoVista> lista) {
        Map<String, long[]> m = new LinkedHashMap<>();
        for (PedidoVista p : lista) {
            Set<String> usados = new HashSet<>();
            for (var pago : p.pagos()) {
                long[] a = m.computeIfAbsent(pago.metodo(), k -> new long[2]);
                a[1] += pago.monto();
                if (usados.add(pago.metodo())) {
                    a[0]++;
                }
            }
        }
        return m.entrySet().stream()
                .map(e -> new PorMetodo(e.getKey(), (int) e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingLong(PorMetodo::monto).reversed())
                .toList();
    }

    private static List<Top> top(List<PedidoVista> lista) {
        Map<String, Top> m = new LinkedHashMap<>();
        for (PedidoVista p : lista) {
            for (var i : p.items()) {
                m.merge(i.tipo() + ":" + i.itemId(), new Top(i.tipo(), i.itemId(), i.nombre(), i.cantidad(), i.subtotal()),
                        (a, b) -> new Top(a.tipo(), a.itemId(), a.nombre(), a.unidades() + b.unidades(), a.monto() + b.monto()));
            }
        }
        return m.values().stream()
                .sorted(Comparator.comparingInt(Top::unidades).reversed().thenComparing(Comparator.comparingLong(Top::monto).reversed()))
                .limit(TOP)
                .toList();
    }

    private static long total(List<PedidoVista> lista) {
        return lista.stream().mapToLong(PedidoVista::total).sum();
    }

    private static long promedio(List<PedidoVista> lista) {
        return lista.isEmpty() ? 0 : Math.round((double) total(lista) / lista.size());
    }
}
