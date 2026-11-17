package cl.primerplayer.checkout.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del checkout ({@code pp.checkout.*}).
 *
 * @param reservaMinutos minutos que el stock y los cupos quedan reservados mientras el cliente paga
 * @param urlRetorno     URL pública a la que Webpay devuelve al cliente ({@code .../api/v1/pagos/webpay/retorno})
 * @param urlResultado   página del frontend que muestra el resultado ({@code ...?pedido=PP-...})
 */
@ConfigurationProperties(prefix = "pp.checkout")
record CheckoutProperties(int reservaMinutos, String urlRetorno, String urlResultado) {
}
