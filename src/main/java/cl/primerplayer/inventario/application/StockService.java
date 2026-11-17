package cl.primerplayer.inventario.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.inventario.api.AjustarStockUseCase;
import cl.primerplayer.inventario.api.InventarioOperaciones;
import cl.primerplayer.inventario.api.StockInsuficienteException;
import cl.primerplayer.inventario.api.TipoMovimiento;
import cl.primerplayer.inventario.application.port.out.MovimientoStockRepositorio;
import cl.primerplayer.inventario.application.port.out.StockRepositorio;
import cl.primerplayer.inventario.domain.MovimientoStock;
import cl.primerplayer.inventario.events.StockRepuesto;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class StockService implements InventarioOperaciones, AjustarStockUseCase {

    private final StockRepositorio stock;
    private final MovimientoStockRepositorio kardex;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    @Override
    @Transactional
    public StockActual ajustar(AjusteStock a) {
        MovimientoStock.validarAjuste(a.delta(), a.motivo());
        int resultante = stock.ajustar(a.productoId(), a.delta())
                .orElseThrow(() -> errorDeStock(0, a.productoId()));
        registrar(MovimientoStock.ajuste(a.productoId(), a.delta(), a.motivo(), a.usuarioId(), resultante,
                clock.instant()));
        return new StockActual(a.productoId(), resultante);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)   // la transacción la abre checkout o pos
    public void descontar(List<LineaStock> lineas, TipoMovimiento tipo, String referencia) {
        Instant ahora = clock.instant();
        enOrden(lineas).forEach(i -> {
            LineaStock l = lineas.get(i);
            int resultante = stock.descontar(l.productoId(), l.cantidad())
                    .orElseThrow(() -> errorDeStock(i, l.productoId()));
            registrar(MovimientoStock.salida(l.productoId(), l.cantidad(), tipo, referencia, resultante, ahora));
        });
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reponer(List<LineaStock> lineas, TipoMovimiento tipo, String referencia) {
        Instant ahora = clock.instant();
        enOrden(lineas).forEach(i -> {
            LineaStock l = lineas.get(i);
            int resultante = stock.reponer(l.productoId(), l.cantidad())
                    .orElseThrow(() -> noExiste(l.productoId()));
            registrar(MovimientoStock.entrada(l.productoId(), l.cantidad(), tipo, referencia, resultante, ahora));
        });
    }

    /** Índices de las líneas ordenados por productoId: todas las transacciones bloquean filas en el mismo orden. */
    private static IntStream enOrden(List<LineaStock> lineas) {
        return IntStream.range(0, lineas.size()).boxed()
                .sorted(Comparator.comparingLong(i -> lineas.get(i).productoId()))
                .mapToInt(Integer::intValue);
    }

    private void registrar(MovimientoStock m) {
        kardex.guardar(m);
        if (m.repuso()) {
            eventos.publishEvent(new StockRepuesto(m.productoId(), m.stockResultante()));
        }
    }

    /** El UPDATE no afectó filas: o el producto no está disponible o no alcanza el stock. */
    private RuntimeException errorDeStock(int linea, long productoId) {
        return stock.stockActivo(productoId)
                .<RuntimeException>map(disponible -> new StockInsuficienteException(linea, productoId, disponible))
                .orElseGet(() -> noExiste(productoId));
    }

    private static RecursoNoEncontradoException noExiste(long productoId) {
        return new RecursoNoEncontradoException("El producto " + productoId + " no existe o fue eliminado.");
    }
}
