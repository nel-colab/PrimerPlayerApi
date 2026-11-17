package cl.primerplayer.notificaciones.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

import cl.primerplayer.clientes.events.AlertaStockDisparada;
import cl.primerplayer.identidad.api.ConsultarUsuarioUseCase;
import cl.primerplayer.identidad.api.UsuarioResumen;
import cl.primerplayer.identidad.events.RecuperacionSolicitada;
import cl.primerplayer.identidad.events.UsuarioRegistrado;
import cl.primerplayer.identidad.events.VerificacionSolicitada;
import cl.primerplayer.notificaciones.application.port.out.EnvioCorreo;
import cl.primerplayer.notificaciones.infrastructure.CorreoEnviadoRepositorio;
import cl.primerplayer.shared.api.FechasTienda;
import cl.primerplayer.ventas.events.PedidoConfirmado;
import lombok.extern.slf4j.Slf4j;

/**
 * Escucha los eventos DESPUÉS del commit, en otro hilo y sin transacción: el SMTP nunca retiene una conexión de base.
 * Spring Modulith guarda cada publicación en el outbox y la reintenta si el envío falla o la app se reinicia.
 * Los logs nunca incluyen tokens (regla 10).
 */
@Service
@Slf4j
class CorreosService {

    private final EnvioCorreo correo;
    private final CorreoEnviadoRepositorio enviados;
    private final ConsultarUsuarioUseCase usuarios;
    private final Clock clock;
    private final String urlFront;

    CorreosService(EnvioCorreo correo, CorreoEnviadoRepositorio enviados, ConsultarUsuarioUseCase usuarios, Clock clock,
                   @Value("${pp.notificaciones.url-front:http://localhost:4200}") String urlFront) {
        this.correo = correo;
        this.enviados = enviados;
        this.usuarios = usuarios;
        this.clock = clock;
        this.urlFront = urlFront.replaceAll("/+$", "");
    }

    @Async
    @TransactionalEventListener
    void alRegistrarse(UsuarioRegistrado e) {
        verificacion(e.email(), e.nombre(), e.tokenVerificacion(), "¡Bienvenido a Primer Player!");
    }

    @Async
    @TransactionalEventListener
    void alPedirVerificacion(VerificacionSolicitada e) {
        verificacion(e.email(), e.nombre(), e.tokenVerificacion(), "Verifica tu correo");
    }

    @Async
    @TransactionalEventListener
    void alPedirRecuperacion(RecuperacionSolicitada e) {
        String enlace = enlace("/cuenta/restablecer", e.tokenRecuperacion());
        enviarUnaVez(e.email(), "recuperacion-password", "token-" + huella(e.tokenRecuperacion()),
                "Restablece tu contraseña · Primer Player", """
                Hola %s:

                Recibimos una solicitud para restablecer la contraseña de tu cuenta en Primer Player.
                Para elegir una nueva, abre este enlace (vence en 1 hora):

                %s

                Si no fuiste tú, ignora este correo: tu contraseña no cambiará.
                """.formatted(e.nombre(), enlace));
    }

    @Async
    @TransactionalEventListener
    void alConfirmarPedido(PedidoConfirmado e) {
        UsuarioResumen cliente = usuarios.buscarPorIds(List.of(e.clienteId())).get(e.clienteId());
        if (cliente == null) {
            return;
        }
        enviarUnaVez(cliente.email(), "pedido-confirmado", e.codigo(), "Tu pedido " + e.codigo() + " está confirmado", """
                Hola %s:

                ¡Gracias por tu compra! Tu pedido %s quedó pagado (total $%s).
                Te avisaremos cuando esté listo para retirar en Av. Portales Ote. 1126, San Bernardo.
                Presenta el código del pedido al retirar.

                Ver mis pedidos: %s/cuenta/perfil
                """.formatted(cliente.nombre(), e.codigo(),
                java.text.NumberFormat.getIntegerInstance(java.util.Locale.of("es", "CL")).format(e.total()), urlFront));
    }

    @Async
    @TransactionalEventListener
    void alReponerseStock(AlertaStockDisparada e) {
        enviarUnaVez(e.email(), "alerta-stock", "producto-" + e.productoId() + "-" + LocalDate.now(clock.withZone(FechasTienda.ZONA)),
                "Volvió el stock: " + e.productoNombre(), """
                Hola %s:

                El producto que esperabas volvió a tener stock: %s.
                Las unidades son limitadas:

                %s/producto/%d
                """.formatted(e.nombre(), e.productoNombre(), urlFront, e.productoId()));
    }

    // ---------- envío ----------

    private void verificacion(String email, String nombre, String token, String asunto) {
        String enlace = enlace("/cuenta/verificar", token);
        enviarUnaVez(email, "verificacion-cuenta", "token-" + huella(token), asunto + " · Primer Player", """
                Hola %s:

                Para activar las compras en Primer Player, confirma tu correo abriendo este enlace (vence en 24 horas):

                %s

                Si no creaste una cuenta, ignora este correo.
                """.formatted(nombre, enlace));
    }

    /**
     * Registra primero (UNIQUE plantilla + referencia + destino): si ya estaba registrado, este correo ya salió y no
     * se repite. Si el envío falla, se borra el registro y la excepción hace que el outbox lo reintente.
     */
    private void enviarUnaVez(String para, String plantilla, String referencia, String asunto, String texto) {
        if (!enviados.reservar(para, plantilla, referencia, clock.instant())) {
            log.info("Correo {} ya enviado (ref {}): se omite", plantilla, referencia);
            return;
        }
        try {
            correo.enviar(para, asunto, texto);
            log.info("Correo {} enviado (ref {})", plantilla, referencia);
        } catch (RuntimeException ex) {
            enviados.liberar(para, plantilla, referencia);
            log.warn("No se pudo enviar el correo {} (ref {}): {}", plantilla, referencia, ex.getMessage());
            throw ex;
        }
    }

    private String enlace(String ruta, String token) {
        return UriComponentsBuilder.fromUriString(urlFront + ruta).queryParam("token", token).build().encode().toUriString();
    }

    /** Referencia estable del token sin guardarlo: 16 caracteres de su SHA-256. */
    private static String huella(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
