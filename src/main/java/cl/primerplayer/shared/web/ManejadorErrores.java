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

    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception ex, HttpServletRequest req) {
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
