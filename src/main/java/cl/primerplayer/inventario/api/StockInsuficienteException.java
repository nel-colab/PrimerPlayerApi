package cl.primerplayer.inventario.api;

import java.util.List;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** {@code out-of-stock} si el stock es 0; {@code stock-limit} si alcanza pero no para la cantidad pedida. */
public class StockInsuficienteException extends ExcepcionNegocio {

    private final int disponible;

    public StockInsuficienteException(int linea, long productoId, int disponible) {
        super(code(disponible), HttpStatus.CONFLICT, detalle(productoId, disponible),
              List.of(new ErrorLinea(linea, "product", productoId, code(disponible), null)));
        this.disponible = disponible;
    }

    public int disponible() { return disponible; }

    private static String code(int disponible) {
        return disponible == 0 ? "out-of-stock" : "stock-limit";
    }

    private static String detalle(long productoId, int disponible) {
        return disponible == 0
                ? "El producto " + productoId + " no tiene stock."
                : "Stock insuficiente para el producto " + productoId + ": quedan " + disponible + ".";
    }
}
