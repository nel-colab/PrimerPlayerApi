package cl.primerplayer.ventas.api;

import java.util.List;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/**
 * Error de negocio de una o más líneas del carrito. {@code code} es el de la primera línea con problema y
 * {@code errors[]} trae todas, con {@code line} = índice en el carrito tal como lo envió el cliente.
 */
public class CarritoInvalidoException extends ExcepcionNegocio {

    public CarritoInvalidoException(String code, String detalle, List<ErrorLinea> errores) {
        super(code, estado(code), detalle, errores);
    }

    public static CarritoInvalidoException vacio() {
        return new CarritoInvalidoException("empty", "El carrito está vacío.", List.of());
    }

    /** {@code empty} e {@code invalid-quantity} son 422; el resto (stock, cupos, límites, disponibilidad) 409. */
    static HttpStatus estado(String code) {
        return switch (code) {
            case "empty", "invalid-quantity" -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.CONFLICT;
        };
    }
}
