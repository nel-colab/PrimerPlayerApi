package cl.primerplayer.shared.api;

import java.util.List;

import org.springframework.http.HttpStatus;

/** Base de todos los errores de negocio. El {@code code} (kebab-case) es contrato con el frontend. */
public abstract class ExcepcionNegocio extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final List<ErrorLinea> errores;

    protected ExcepcionNegocio(String code, HttpStatus status, String detalle) {
        this(code, status, detalle, null);
    }

    protected ExcepcionNegocio(String code, HttpStatus status, String detalle, List<ErrorLinea> errores) {
        super(detalle);
        this.code = code;
        this.status = status;
        this.errores = errores == null ? List.of() : List.copyOf(errores);
    }

    public String code() { return code; }
    public HttpStatus status() { return status; }
    public List<ErrorLinea> errores() { return errores; }

    public record ErrorLinea(int line, String itemType, long itemId, String code, Integer maxAllowed) {}
}
