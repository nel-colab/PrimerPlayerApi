package cl.primerplayer.shared.api;

import java.util.List;

/**
 * Error de validación detectado fuera de Bean Validation (p. ej. en un servicio).
 * Se responde igual que un {@code @Valid} fallido: 400 con {@code code = validation} y {@code errors[].field}.
 */
public class ValidacionException extends RuntimeException {

    private final List<ErrorCampo> errores;

    public ValidacionException(String campo, String mensaje) {
        super(mensaje);
        this.errores = List.of(new ErrorCampo(campo, mensaje));
    }

    public List<ErrorCampo> errores() { return errores; }

    public record ErrorCampo(String field, String message) {}
}
