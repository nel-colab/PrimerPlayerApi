package cl.primerplayer.identidad.api;

import cl.primerplayer.identidad.api.CuentaUseCase.Perfil;
import cl.primerplayer.identidad.api.IniciarSesionUseCase.SesionIniciada;

/**
 * Puerto de entrada: identidad con Google (botón "Continuar con Google" del frontend, Google Identity Services).
 * El {@code credential} es el ID token (JWT) que Google entrega al navegador; el servidor lo valida contra las llaves
 * públicas de Google, el emisor y el client id de la tienda. Google solo cuenta si confirma el correo
 * ({@code email_verified}).
 */
public interface GoogleUseCase {

    /** Client id público para el botón del frontend; vacío si Google no está configurado. */
    java.util.Optional<String> clientId();

    /**
     * Inicia sesión con Google. Si el correo ya tiene cuenta (registrada con contraseña o creada por la tienda) la
     * vincula y marca el correo como verificado; si no existe, crea una cuenta CLIENTE sin contraseña.
     *
     * @throws GoogleTokenInvalidoException 401 {@code invalid-google-token}
     * @throws GoogleCuentaEnUsoException   409 {@code google-account-in-use}: el correo ya está vinculado a otra cuenta de Google
     * @throws CuentaBaneadaException       423 {@code account-banned}
     * @throws GoogleNoDisponibleException  503 {@code google-unavailable}
     */
    SesionIniciada iniciarSesion(String credential, String ip, String userAgent);

    /**
     * Verifica el correo de la cuenta con sesión usando Google (alternativa al enlace por correo) y vincula la
     * cuenta de Google. Idempotente si ya estaba vinculada a la misma cuenta de Google.
     *
     * @throws GoogleCorreoDistintoException 409 {@code google-email-mismatch}: Google confirma otro correo
     * @throws GoogleCuentaEnUsoException    409 {@code google-account-in-use}
     */
    Perfil verificarCorreo(long usuarioId, String credential);
}
