package cl.primerplayer.identidad.api;

import java.time.Duration;
import java.util.Optional;

/**
 * Emite los enlaces de un solo uso (verificar correo, restablecer contraseña) en el momento de enviar el correo.
 * <p>El token en claro solo existe en memoria y en el correo: en la base queda su SHA-256 y los eventos del outbox
 * no lo llevan. Cada emisión invalida los enlaces anteriores del mismo tipo que no se usaron, así que solo sirve
 * el último correo.
 * <p>Cada método abre su propia transacción corta: se llama fuera de transacción, antes del envío SMTP (regla 4).
 */
public interface EnlacesCuentaUseCase {

    /** @return vacío si la cuenta ya no existe, está inactiva o ya verificó su correo (no hay nada que enviar) */
    Optional<TokenEmitido> emitirVerificacion(long usuarioId);

    /** @return vacío si la cuenta ya no existe o está inactiva */
    Optional<TokenEmitido> emitirRecuperacion(long usuarioId);

    /** @param vigencia para el texto del correo ("vence en 24 horas") */
    record TokenEmitido(String valor, Duration vigencia) {
        @Override
        public String toString() {
            return "TokenEmitido[***]";   // nunca el token en los logs (regla 10)
        }
    }
}
