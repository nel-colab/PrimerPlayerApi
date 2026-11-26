package cl.primerplayer.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** El {@code title} del problem+json: cada {@code code} de negocio que usa la API tiene un título propio. */
class TitulosTest {

    /** Los códigos que el frontend interpreta (ver api-problem.ts del front). */
    static final String[] CODIGOS = {
        "validation", "not-found", "method-not-allowed", "unsupported-media-type", "not-acceptable",
        "payload-too-large", "unauthorized", "forbidden", "account-locked", "email-taken", "email-not-verified",
        "rate-limited", "out-of-stock", "stock-limit", "purchase-limit", "item-unavailable", "barcode-taken",
        "barcode-immutable", "version-conflict", "limit-admin-only", "precondition-required", "event-full",
        "event-past", "slot-taken", "request-not-pending", "order-invalid-state", "empty", "invalid-quantity",
        "idempotency-conflict", "payment-provider-error", "registration-not-cancellable", "payments-mismatch",
        "receipt-required", "transfer-proof-required", "tcg-card-required", "cash-change-mismatch",
        "storage-unavailable", "invalid-token", "already-verified", "account-banned", "blacklisted", "self-action",
        "last-admin", "profile-incomplete", "invalid-google-token", "google-unavailable", "google-email-mismatch",
        "google-account-in-use",
    };

    @Test
    void cadaCodigoTieneUnTituloPropioYDistinto() {
        Set<String> titulos = new HashSet<>();
        for (String code : CODIGOS) {
            String titulo = Titulos.de(code);
            assertThat(titulo).as(code).isNotBlank().isNotEqualTo("Error de negocio");
            assertThat(titulos.add(titulo)).as("título repetido para %s: %s", code, titulo).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"codigo-que-no-existe", "", "VALIDATION"})
    void unCodigoDesconocidoUsaElTituloGenerico(String code) {
        assertThat(Titulos.de(code)).isEqualTo("Error de negocio");
    }

    @Test
    void ejemplosConcretos() {
        assertThat(Titulos.de("out-of-stock")).isEqualTo("Sin stock");
        assertThat(Titulos.de("purchase-limit")).isEqualTo("Límite de compra superado");
        assertThat(Titulos.de("account-banned")).isEqualTo("Cuenta suspendida");
    }
}
