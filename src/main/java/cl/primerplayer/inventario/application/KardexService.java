package cl.primerplayer.inventario.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.inventario.api.ConsultarKardexUseCase;
import cl.primerplayer.inventario.application.port.out.MovimientoStockRepositorio;
import cl.primerplayer.inventario.application.port.out.StockRepositorio;
import cl.primerplayer.inventario.domain.MovimientoStock;
import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class KardexService implements ConsultarKardexUseCase {

    private final MovimientoStockRepositorio kardex;
    private final StockRepositorio stock;

    @Override
    @Transactional(readOnly = true)
    public Pagina<Movimiento> movimientos(long productoId, int page, int size) {
        if (!stock.existe(productoId)) {
            throw new RecursoNoEncontradoException("El producto " + productoId + " no existe.");
        }
        return kardex.deProducto(productoId, page, size).map(KardexService::vista);
    }

    private static Movimiento vista(MovimientoStock m) {
        return new Movimiento(m.id(), m.tipo(), m.cantidad(), m.stockResultante(), m.referencia(), m.motivo(),
                m.usuarioId(), m.creadoEn());
    }
}
