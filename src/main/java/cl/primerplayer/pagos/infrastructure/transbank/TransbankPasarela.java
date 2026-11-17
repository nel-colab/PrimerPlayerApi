package cl.primerplayer.pagos.infrastructure.transbank;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;

import cl.primerplayer.pagos.api.PasarelaNoDisponibleException;
import cl.primerplayer.pagos.api.PasarelaWebpay;
import cl.transbank.common.IntegrationApiKeys;
import cl.transbank.common.IntegrationCommerceCodes;
import cl.transbank.webpay.webpayplus.WebpayPlus;
import cl.transbank.webpay.webpayplus.responses.WebpayPlusTransactionCreateResponse;
import cl.transbank.webpay.webpayplus.responses.WebpayPlusTransactionStatusResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador del SDK oficial {@code transbank-sdk-java}. Los logs nunca incluyen tokens ni datos de tarjeta
 * (regla 10): solo el buy order (código del pedido) y el estado.
 */
@Component
@EnableConfigurationProperties(WebpayProperties.class)
@Slf4j
class TransbankPasarela implements PasarelaWebpay {

    private static final Gson GSON = new Gson();

    private final WebpayPlus.Transaction transaccion;
    private final String urlFormulario;

    TransbankPasarela(WebpayProperties props) {
        if (props.esProduccion()) {
            if (vacio(props.codigoComercio()) || vacio(props.apiKey())) {
                throw new IllegalStateException(
                        "Webpay en producción exige PP_WEBPAY_CODIGO_COMERCIO y PP_WEBPAY_API_KEY.");
            }
            this.transaccion = WebpayPlus.Transaction.buildForProduction(props.codigoComercio(), props.apiKey());
        } else {
            this.transaccion = WebpayPlus.Transaction.buildForIntegration(
                    vacio(props.codigoComercio()) ? IntegrationCommerceCodes.WEBPAY_PLUS : props.codigoComercio(),
                    vacio(props.apiKey()) ? IntegrationApiKeys.WEBPAY : props.apiKey());
        }
        this.urlFormulario = props.urlFormulario();
        log.info("Webpay Plus configurado en ambiente {}", props.esProduccion() ? "PRODUCCIÓN" : "integración (pruebas)");
    }

    @Override
    public InicioWebpay crear(String buyOrder, String sessionId, long monto, String urlRetorno) {
        try {
            WebpayPlusTransactionCreateResponse r = transaccion.create(buyOrder, sessionId, monto, urlRetorno);
            return new InicioWebpay(r.getToken(), r.getUrl());
        } catch (IOException e) {
            log.warn("Webpay no respondió al crear el pago del pedido {}: {}", buyOrder, e.getMessage());
            throw new PasarelaNoDisponibleException("No pudimos conectar con Webpay. Intenta nuevamente en unos minutos.");
        } catch (Exception e) {   // TransactionCreateException: datos rechazados por Transbank
            log.error("Webpay rechazó crear el pago del pedido {}: {}", buyOrder, e.getMessage());
            throw new PasarelaNoDisponibleException("Webpay rechazó iniciar el pago. Intenta nuevamente.");
        }
    }

    @Override
    public Optional<ResultadoWebpay> confirmar(String token) {
        try {
            return Optional.of(mapear(transaccion.commit(token)));
        } catch (IOException e) {
            throw new PasarelaNoDisponibleException("Webpay no respondió al confirmar el pago.");
        } catch (Exception e) {   // TransactionCommitException: token inválido, vencido o ya confirmado
            log.warn("Webpay no aceptó confirmar una transacción: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<ResultadoWebpay> consultar(String token) {
        try {
            return Optional.of(mapear(transaccion.status(token)));
        } catch (IOException e) {
            throw new PasarelaNoDisponibleException("Webpay no respondió al consultar el pago.");
        } catch (Exception e) {   // TransactionStatusException
            log.info("Webpay no tiene resultado para una transacción: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public String urlFormulario() {
        return urlFormulario;
    }

    private static ResultadoWebpay mapear(WebpayPlusTransactionStatusResponse r) {
        boolean autorizado = r.getResponseCode() == 0 && "AUTHORIZED".equals(r.getStatus());
        // Solo AUTHORIZED/FAILED traen un response_code real; en otros estados (p. ej. INITIALIZED) el SDK deja 0,
        // que se confundiría con "aprobado".
        boolean conCodigo = "AUTHORIZED".equals(r.getStatus()) || "FAILED".equals(r.getStatus());
        String last4 = r.getCardDetail() == null ? null : r.getCardDetail().getCardNumber();
        return new ResultadoWebpay(autorizado, r.getStatus(), conCodigo ? (int) r.getResponseCode() : null,
                Math.round(r.getAmount()),
                r.getBuyOrder(), r.getAuthorizationCode(), r.getPaymentTypeCode(), (int) r.getInstallmentsNumber(),
                last4, r.getAccountingDate(), instante(r.getTransactionDate()), GSON.toJson(r));
    }

    private static Instant instante(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(iso);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}
