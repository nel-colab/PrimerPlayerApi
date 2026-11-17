package cl.primerplayer.pagos.api;

import java.time.Instant;
import java.util.Optional;

/**
 * Llamadas HTTP a Webpay Plus. <b>Nunca dentro de una transacción de base de datos</b> (regla 4 del proyecto):
 * quien orquesta guarda antes y después.
 */
public interface PasarelaWebpay {

    /**
     * Crea la transacción en Transbank. El cliente debe ir a {@code url} enviando {@code token_ws = token} (POST).
     *
     * @param buyOrder código del pedido (máx. 26 caracteres)
     * @throws PasarelaNoDisponibleException si Transbank no responde o rechaza la creación
     */
    InicioWebpay crear(String buyOrder, String sessionId, long monto, String urlRetorno);

    /**
     * Confirma (commit) el pago al volver el cliente. Una tarjeta rechazada también responde: con
     * {@code autorizado = false}.
     *
     * @return vacío si Transbank no acepta el commit (token inválido, vencido o ya confirmado)
     * @throws PasarelaNoDisponibleException si no hay respuesta (red, timeout): reintentar más tarde
     */
    Optional<ResultadoWebpay> confirmar(String token);

    /**
     * Estado de una transacción (para resolver retornos perdidos y reservas vencidas).
     *
     * @return vacío si Transbank no tiene un resultado para ese token
     * @throws PasarelaNoDisponibleException si no hay respuesta
     */
    Optional<ResultadoWebpay> consultar(String token);

    /** URL del formulario de pago del ambiente configurado (para reenviar a un cliente que reintenta). */
    String urlFormulario();

    record InicioWebpay(String token, String url) {}

    /**
     * Resultado de Transbank. {@code cardLast4} son los últimos 4 dígitos: nunca hay número de tarjeta completo.
     *
     * @param json respuesta completa (sin datos sensibles) para auditoría ({@code respuesta_commit})
     */
    record ResultadoWebpay(boolean autorizado, String status, Integer responseCode, long monto, String buyOrder,
                           String authorizationCode, String paymentTypeCode, Integer cuotas, String cardLast4,
                           String accountingDate, Instant transactionDate, String json) {

        /** Autorizado y por el monto exacto del pedido. */
        public boolean aceptablePara(long montoEsperado) {
            return autorizado && monto == montoEsperado;
        }
    }
}
