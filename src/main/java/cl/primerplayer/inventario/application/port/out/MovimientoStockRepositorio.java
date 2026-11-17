package cl.primerplayer.inventario.application.port.out;

import cl.primerplayer.inventario.domain.MovimientoStock;
import cl.primerplayer.shared.api.Pagina;

public interface MovimientoStockRepositorio {

    void guardar(MovimientoStock movimiento);

    Pagina<MovimientoStock> deProducto(long productoId, int page, int size);
}
