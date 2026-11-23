package cl.primerplayer.shared.web;

import java.net.URI;
import java.util.List;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.ErrorResponse;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import cl.primerplayer.shared.api.ExcepcionNegocio;
import cl.primerplayer.shared.api.ValidacionException;
import cl.primerplayer.shared.api.ValidacionException.ErrorCampo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/** Convierte los errores en {@code application/problem+json} (RFC 9457) con un {@code code} de negocio. */
@RestControllerAdvice
@Slf4j
class ManejadorErrores {

    static final String BASE_TIPO = "https://api.primerplayer.cl/problemas/";

    @ExceptionHandler(ExcepcionNegocio.class)
    ProblemDetail negocio(ExcepcionNegocio ex, HttpServletRequest req) {
        ProblemDetail pd = problema(ex.status(), ex.code(), ex.getMessage(), req);
        if (!ex.errores().isEmpty()) {
            pd.setProperty("errors", ex.errores());
        }
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail beanValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ErrorCampo> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorCampo(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return validacion(errores, req);
    }

    @ExceptionHandler(ValidacionException.class)
    ProblemDetail validacionServicio(ValidacionException ex, HttpServletRequest req) {
        return validacion(ex.errores(), req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail cuerpoIlegible(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return problema(HttpStatus.BAD_REQUEST, "validation", "El cuerpo de la solicitud no es JSON válido.", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accesoDenegado(AccessDeniedException ex, HttpServletRequest req) {
        return problema(HttpStatus.FORBIDDEN, "forbidden", "No tienes permiso para esta operación.", req);
    }

    /** {@code /productos/abc} donde se espera un número, o un enum desconocido en un parámetro. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail tipoInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return validacion(List.of(new ErrorCampo(ex.getName(), "Valor inválido.")), req);
    }

    /**
     * Errores propios de Spring MVC (ruta inexistente, método no permitido, parámetro faltante, archivo demasiado
     * grande, tipo de contenido no soportado...): conservan su estado HTTP. Sin esto caían en el 500 genérico, y cada
     * bot que prueba rutas al azar dejaba un "Error inesperado" en el log.
     */
    private static ProblemDetail errorMvc(HttpStatus status, HttpServletRequest req) {
        return switch (status) {
            case NOT_FOUND -> problema(status, "not-found", "El recurso no existe.", req);
            case METHOD_NOT_ALLOWED -> problema(status, "method-not-allowed", "Método no permitido.", req);
            case UNSUPPORTED_MEDIA_TYPE -> problema(status, "unsupported-media-type", "Tipo de contenido no soportado.", req);
            case NOT_ACCEPTABLE -> problema(status, "not-acceptable", "Formato de respuesta no disponible.", req);
            case CONTENT_TOO_LARGE -> problema(status, "payload-too-large", "El archivo es demasiado grande.", req);
            default -> problema(status, "validation", "La solicitud no es válida.", req);
        };
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception ex, HttpServletRequest req) {
        if (ex instanceof ErrorResponse er) {
            HttpStatus status = HttpStatus.resolve(er.getStatusCode().value());
            if (status != null && status.is4xxClientError()) {
                return errorMvc(status, req);
            }
        }
        log.error("Error inesperado en {} {}", req.getMethod(), req.getRequestURI(), ex);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "server-error", "Error interno del servidor.", req);
    }

    private ProblemDetail validacion(List<ErrorCampo> errores, HttpServletRequest req) {
        ProblemDetail pd = problema(HttpStatus.BAD_REQUEST, "validation", "La solicitud tiene campos inválidos.", req);
        pd.setProperty("errors", errores);
        return pd;
    }

    private static ProblemDetail problema(HttpStatus status, String code, String detalle, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detalle);
        pd.setType(URI.create(BASE_TIPO + code));
        pd.setTitle(Titulos.de(code));
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("code", code);
        String requestId = MDC.get("requestId");
        if (requestId != null) {
            pd.setProperty("requestId", requestId);
        }
        return pd;
    }
}
