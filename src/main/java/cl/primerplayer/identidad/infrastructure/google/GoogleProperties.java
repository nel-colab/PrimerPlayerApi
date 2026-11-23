package cl.primerplayer.identidad.infrastructure.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code pp.auth.google.*}. El client id es el "ID de cliente OAuth" de tipo aplicación web creado en Google Cloud
 * Console (el mismo que usa el frontend). Vacío = inicio de sesión con Google deshabilitado (503 google-unavailable).
 */
@ConfigurationProperties("pp.auth.google")
record GoogleProperties(String clientId) {}
