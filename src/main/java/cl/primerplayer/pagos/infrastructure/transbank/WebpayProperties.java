package cl.primerplayer.pagos.infrastructure.transbank;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de Webpay Plus ({@code pp.webpay.*}).
 *
 * @param ambiente       {@code integracion} (pruebas, credenciales públicas del SDK) o {@code produccion}
 * @param codigoComercio obligatorio en producción ({@code PP_WEBPAY_CODIGO_COMERCIO}); en integración, vacío = el del SDK
 * @param apiKey         obligatoria en producción ({@code PP_WEBPAY_API_KEY}); en integración, vacía = la del SDK
 * @param urlFormulario  URL del formulario de pago, para reenviar a un cliente que reintenta el mismo pedido
 */
@ConfigurationProperties(prefix = "pp.webpay")
record WebpayProperties(String ambiente, String codigoComercio, String apiKey, String urlFormulario) {

    boolean esProduccion() {
        return "produccion".equalsIgnoreCase(ambiente);
    }
}
