package cl.primerplayer.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import cl.primerplayer.shared.api.ExcepcionNegocio;
import cl.primerplayer.shared.api.ExcepcionNegocio.ErrorLinea;

/** Ramas del manejador que las pruebas de integración no recorren (errores MVC, 5xx, requestId). */
class ManejadorErroresTest {

    final ManejadorErrores manejador = new ManejadorErrores();
    final MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/productos/1");

    @AfterEach
    void limpiarMdc() {
        MDC.clear();
    }

    static class SinStock extends ExcepcionNegocio {
        SinStock(List<ErrorLinea> errores) {
            super("out-of-stock", HttpStatus.CONFLICT, "Sin stock", errores);
        }
    }

    @Test
    void negocioSinErroresNoAgregaLaLista() {
        ProblemDetail pd = manejador.negocio(new SinStock(List.of()), req);

        assertThat(pd.getStatus()).isEqualTo(409);
        assertThat(pd.getProperties()).containsEntry("code", "out-of-stock").doesNotContainKey("errors");
        assertThat(pd.getType().toString()).endsWith("/problemas/out-of-stock");
        assertThat(pd.getInstance().toString()).isEqualTo("/api/v1/productos/1");
    }

    @Test
    void requestIdDelMdcViajaEnLaRespuesta() {
        MDC.put("requestId", "req-123");

        ProblemDetail pd = manejador.negocio(new SinStock(List.of()), req);

        assertThat(pd.getProperties()).containsEntry("requestId", "req-123");
    }

    @ParameterizedTest
    @CsvSource({
            "NOT_FOUND, not-found",
            "METHOD_NOT_ALLOWED, method-not-allowed",
            "UNSUPPORTED_MEDIA_TYPE, unsupported-media-type",
            "NOT_ACCEPTABLE, not-acceptable",
            "CONTENT_TOO_LARGE, payload-too-large",
            "BAD_REQUEST, validation",
    })
    void erroresMvcConservanSuEstadoYCodigo(HttpStatus status, String code) {
        ProblemDetail pd = manejador.inesperado(new ResponseStatusException(status), req);

        assertThat(pd.getStatus()).isEqualTo(status.value());
        assertThat(pd.getProperties()).containsEntry("code", code);
    }

    @Test
    void errorMvc5xxEsErrorInterno() {
        ProblemDetail pd = manejador.inesperado(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE), req);
        assertThat(pd.getStatus()).isEqualTo(500);
        assertThat(pd.getProperties()).containsEntry("code", "server-error");
    }

    @Test
    void errorMvcConEstadoNoEstandarEsErrorInterno() {
        ProblemDetail pd = manejador.inesperado(new ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(499)), req);
        assertThat(pd.getProperties()).containsEntry("code", "server-error");
    }

    @Test
    void excepcionCualquieraEsErrorInternoSinDetallesInternos() {
        req.setMethod(HttpMethod.POST.name());

        ProblemDetail pd = manejador.inesperado(new IllegalStateException("password=secreto"), req);

        assertThat(pd.getStatus()).isEqualTo(500);
        assertThat(pd.getDetail()).doesNotContain("secreto");
    }
}
