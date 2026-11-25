package cl.primerplayer.identidad.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import cl.primerplayer.shared.api.Rol;

/** Puerto de entrada: la cuenta del propio usuario (perfil, contraseña, verificación y recuperación). */
public interface CuentaUseCase {

    Perfil perfil(long usuarioId);

    /**
     * @param telefono opcional (dígitos, espacios y +; máx. 20)
     * @param rut      opcional; con dígito verificador válido (acepta puntos y guion)
     * @throws cl.primerplayer.shared.api.ValidacionException datos inválidos
     */
    Perfil actualizarPerfil(long usuarioId, String nombre, String telefono, String rut);

    /**
     * Cambia la contraseña y cierra las demás sesiones (revoca todos los refresh tokens).
     *
     * @throws cl.primerplayer.shared.api.ValidacionException si la actual no coincide o la nueva no cumple la política
     */
    void cambiarPassword(long usuarioId, String actual, String nueva);

    /**
     * Verifica el correo con el código de 6 dígitos que llegó por correo.
     *
     * @throws CodigoInvalidoException      400 {@code invalid-code} si no corresponde, venció, ya se usó o agotó sus
     *                                      intentos (el mismo error si el correo no está registrado)
     * @throws DemasiadosIntentosException  429 {@code too-many-attempts} tras 10 códigos errados en una hora
     */
    void verificarEmail(String email, String codigo);

    /** @throws YaVerificadoException 409 {@code already-verified} */
    void reenviarVerificacion(long usuarioId);

    /** Siempre "acepta" (202): no revela si el correo existe. Si existe, envía el código de restablecimiento. */
    void solicitarRecuperacion(String email);

    /**
     * Define una contraseña nueva con el código de recuperación; cierra todas las sesiones y desbloquea la cuenta.
     *
     * @throws CodigoInvalidoException     400 {@code invalid-code}
     * @throws DemasiadosIntentosException 429 {@code too-many-attempts}
     */
    void restablecerPassword(String email, String codigo, String nueva);

    record Perfil(long id, String email, String nombre, Rol rol, String telefono, String rut, Instant miembroDesde,
                  boolean emailVerificado, boolean cambioPasswordPendiente) {

        /** Campos de información personal que faltan (nombre, teléfono, RUT). Vacío = perfil completo. */
        public List<String> camposFaltantes() {
            List<String> faltan = new ArrayList<>(3);
            if (nombre == null || nombre.isBlank()) faltan.add("nombre");
            if (telefono == null || telefono.isBlank()) faltan.add("telefono");
            if (rut == null || rut.isBlank()) faltan.add("rut");
            return faltan;
        }

        public boolean completo() {
            return camposFaltantes().isEmpty();
        }
    }
}
