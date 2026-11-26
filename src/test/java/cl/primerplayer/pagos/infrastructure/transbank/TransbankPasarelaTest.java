package cl.primerplayer.pagos.infrastructure.transbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import cl.primerplayer.pagos.api.PasarelaNoDisponibleException;
import cl.primerplayer.pagos.api.PasarelaWebpay.InicioWebpay;
import cl.primerplayer.pagos.api.PasarelaWebpay.ResultadoWebpay;
import cl.transbank.common.IntegrationApiKeys;
import cl.transbank.common.IntegrationCommerceCodes;
import cl.transbank.model.CardDetail;
import cl.transbank.webpay.exception.TransactionCommitException;
import cl.transbank.webpay.exception.TransactionCreateException;
import cl.transbank.webpay.exception.TransactionStatusException;
import cl.transbank.webpay.webpayplus.WebpayPlus;
import cl.transbank.webpay.webpayplus.responses.WebpayPlusTransactionCommitResponse;
import cl.transbank.webpay.webpayplus.responses.WebpayPlusTransactionCreateResponse;
import cl.transbank.webpay.webpayplus.responses.WebpayPlusTransactionStatusResponse;

/** El SDK se reemplaza con un mock: ninguna prueba llama a Transbank. */
class TransbankPasarelaTest {

    static final String URL_FORM = "https://webpay3gint.transbank.cl/webpayserver/initTransaction";

    final WebpayPlus.Transaction tx = mock(WebpayPlus.Transaction.class);
    final MockedStatic<WebpayPlus.Transaction> sdk = mockStatic(WebpayPlus.Transaction.class);

    @AfterEach
    void cerrar() {
        sdk.close();
    }

    TransbankPasarela integracion() {
        sdk.when(() -> WebpayPlus.Transaction.buildForIntegration(anyString(), anyString())).thenReturn(tx);
        return new TransbankPasarela(new WebpayProperties("integracion", "", null, URL_FORM));
    }

    static <T extends WebpayPlusTransactionStatusResponse> T respuesta(T r, Consumer<T> ajuste) {
        r.setStatus("AUTHORIZED");
        r.setResponseCode((byte) 0);
        r.setAmount(15990);
        r.setBuyOrder("PP-2026-000123");
        r.setAuthorizationCode("1213");
        r.setPaymentTypeCode("VD");
        r.setInstallmentsNumber((byte) 0);
        r.setCardDetail(new CardDetail("6623"));
        r.setAccountingDate("1126");
        r.setTransactionDate("2026-11-26T15:00:00Z");
        ajuste.accept(r);
        return r;
    }

    // ---------- configuración ----------

    @Test
    void integracionSinCredencialesUsaLasPublicasDelSdk() {
        TransbankPasarela p = integracion();

        sdk.verify(() -> WebpayPlus.Transaction.buildForIntegration(
                IntegrationCommerceCodes.WEBPAY_PLUS, IntegrationApiKeys.WEBPAY));
        assertThat(p.urlFormulario()).isEqualTo(URL_FORM);
    }

    @Test
    void integracionConCredencialesPropiasLasRespeta() {
        sdk.when(() -> WebpayPlus.Transaction.buildForIntegration(anyString(), anyString())).thenReturn(tx);

        new TransbankPasarela(new WebpayProperties("integracion", "597000000001", "clave-propia", URL_FORM));

        sdk.verify(() -> WebpayPlus.Transaction.buildForIntegration("597000000001", "clave-propia"));
    }

    @Test
    void produccionConCredencialesUsaBuildForProduction() {
        sdk.when(() -> WebpayPlus.Transaction.buildForProduction(anyString(), anyString())).thenReturn(tx);

        new TransbankPasarela(new WebpayProperties("PRODUCCION", "597012345678", "clave-real", URL_FORM));

        sdk.verify(() -> WebpayPlus.Transaction.buildForProduction("597012345678", "clave-real"));
    }

    @Test
    void produccionSinCodigoDeComercioNoArranca() {
        assertThatThrownBy(() -> new TransbankPasarela(new WebpayProperties("produccion", " ", "clave", URL_FORM)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PP_WEBPAY_CODIGO_COMERCIO");
    }

    @Test
    void produccionSinApiKeyNoArranca() {
        assertThatThrownBy(() -> new TransbankPasarela(new WebpayProperties("produccion", "597012345678", null, URL_FORM)))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------- crear ----------

    @Test
    void crearDevuelveTokenYUrl() throws Exception {
        TransbankPasarela p = integracion();
        when(tx.create("PP-1", "s-1", 15990.0, "https://retorno"))
                .thenReturn(new WebpayPlusTransactionCreateResponse("tok-123", URL_FORM));

        InicioWebpay inicio = p.crear("PP-1", "s-1", 15990, "https://retorno");

        assertThat(inicio).isEqualTo(new InicioWebpay("tok-123", URL_FORM));
    }

    @Test
    void crearSinRedEsPasarelaNoDisponible() throws Exception {
        TransbankPasarela p = integracion();
        when(tx.create(anyString(), anyString(), org.mockito.ArgumentMatchers.anyDouble(), anyString()))
                .thenThrow(new IOException("timeout"));

        assertThatThrownBy(() -> p.crear("PP-1", "s-1", 1000, "https://retorno"))
                .isInstanceOf(PasarelaNoDisponibleException.class)
                .hasMessageContaining("No pudimos conectar");
    }

    @Test
    void crearRechazadoPorTransbankEsPasarelaNoDisponible() throws Exception {
        TransbankPasarela p = integracion();
        when(tx.create(anyString(), anyString(), org.mockito.ArgumentMatchers.anyDouble(), anyString()))
                .thenThrow(new TransactionCreateException("monto inválido"));

        assertThatThrownBy(() -> p.crear("PP-1", "s-1", 1000, "https://retorno"))
                .isInstanceOf(PasarelaNoDisponibleException.class)
                .hasMessageContaining("rechazó");
    }

    // ---------- confirmar ----------

    @Test
    void confirmarAutorizadoMapeaTodosLosCampos() throws Exception {
        TransbankPasarela p = integracion();
        when(tx.commit("tok")).thenReturn(respuesta(new WebpayPlusTransactionCommitResponse(), r -> {}));

        ResultadoWebpay res = p.confirmar("tok").orElseThrow();

        assertThat(res.autorizado()).isTrue();
        assertThat(res.aceptablePara(15990)).isTrue();
        assertThat(res.status()).isEqualTo("AUTHORIZED");
        assertThat(res.responseCode()).isZero();
        assertThat(res.monto()).isEqualTo(15990);
        assertThat(res.buyOrder()).isEqualTo("PP-2026-000123");
        assertThat(res.authorizationCode()).isEqualTo("1213");
        assertThat(res.paymentTypeCode()).isEqualTo("VD");
        assertThat(res.cuotas()).isZero();
        assertThat(res.cardLast4()).isEqualTo("6623");
        assertThat(res.accountingDate()).isEqualTo("1126");
        assertThat(res.transactionDate()).isEqualTo(Instant.parse("2026-11-26T15:00:00Z"));
        assertThat(res.json()).contains("PP-2026-000123");
    }

    @Test
    void confirmarRechazadoTraeCodigoYNoEsAutorizado() throws Exception {
        TransbankPasarela p = integracion();
        when(tx.commit("tok")).thenReturn(respuesta(new WebpayPlusTransactionCommitResponse(), r -> {
            r.setStatus("FAILED");
            r.setResponseCode((byte) -1);
        }));

        ResultadoWebpay res = p.confirmar("tok").orElseThrow();

        assertThat(res.autorizado()).isFalse();
        assertThat(res.responseCode()).isEqualTo(-1);
    }

    @Test
    void confirmarConCodigoDistintoDeCeroNoEsAutorizadoAunqueDigaAuthorized() throws Exception {
        TransbankPasarela p = integracion();
        when(tx.commit("tok")).thenReturn(respuesta(new WebpayPlusTransactionCommitResponse(),
                r -> r.setResponseCode((byte) -3)));

        assertThat(p.confirmar("tok").orElseThrow().autorizado()).isFalse();
    }

    @Test
    void confirmarSinRedEsPasarelaNoDisponible() throws Exception {
        TransbankPasarela p = integracion();
        when(tx.commit("tok")).thenThrow(new IOException("sin red"));

        assertThatThrownBy(() -> p.confirmar("tok")).isInstanceOf(PasarelaNoDisponibleException.class);
    }

    @Test
    void confirmarTokenInvalidoDevuelveVacio() throws Exception {
        TransbankPasarela p = integracion();
        when(tx.commit("tok")).thenThrow(new TransactionCommitException("token ya confirmado"));

        assertThat(p.confirmar("tok")).isEmpty();
    }

    // ---------- consultar ----------

    @Test
    void consultarInicializadaNoTraeCodigoNiTarjetaNiFecha() throws Exception {
        TransbankPasarela p = integracion();
        when(tx.status("tok")).thenReturn(respuesta(new WebpayPlusTransactionStatusResponse(), r -> {
            r.setStatus("INITIALIZED");
            r.setCardDetail(null);
            r.setTransactionDate(null);
        }));

        ResultadoWebpay res = p.consultar("tok").orElseThrow();

        assertThat(res.autorizado()).isFalse();
        assertThat(res.responseCode()).isNull();
        assertThat(res.cardLast4()).isNull();
        assertThat(res.transactionDate()).isNull();
    }

    @Test
    void consultarConFechaEnBlancoOInvalidaLaDejaNula() throws Exception {
        TransbankPasarela p = integracion();
        when(tx.status("blanco")).thenReturn(respuesta(new WebpayPlusTransactionStatusResponse(),
                r -> r.setTransactionDate("  ")));
        when(tx.status("invalida")).thenReturn(respuesta(new WebpayPlusTransactionStatusResponse(),
                r -> r.setTransactionDate("26/11/2026")));

        assertThat(p.consultar("blanco").orElseThrow().transactionDate()).isNull();
        assertThat(p.consultar("invalida").orElseThrow().transactionDate()).isNull();
    }

    @Test
    void consultarSinRedEsPasarelaNoDisponible() throws Exception {
        TransbankPasarela p = integracion();
        when(tx.status("tok")).thenThrow(new IOException("sin red"));

        assertThatThrownBy(() -> p.consultar("tok")).isInstanceOf(PasarelaNoDisponibleException.class);
    }

    @Test
    void consultarSinResultadoDevuelveVacio() throws Exception {
        TransbankPasarela p = integracion();
        when(tx.status("tok")).thenThrow(new TransactionStatusException("no existe"));

        assertThat(p.consultar("tok")).isEmpty();
    }
}
