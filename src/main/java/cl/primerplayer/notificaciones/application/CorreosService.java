package cl.primerplayer.notificaciones.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

import cl.primerplayer.catalogo.events.ProductoEnRevision;
import cl.primerplayer.clientes.events.AlertaStockDisparada;
import cl.primerplayer.identidad.api.ConsultarUsuarioUseCase;
import cl.primerplayer.identidad.api.EnlacesCuentaUseCase;
import cl.primerplayer.identidad.api.EnlacesCuentaUseCase.TokenEmitido;
import cl.primerplayer.identidad.api.UsuarioResumen;
import cl.primerplayer.identidad.events.RecuperacionSolicitada;
import cl.primerplayer.identidad.events.UsuarioRegistrado;
import cl.primerplayer.identidad.events.VerificacionSolicitada;
import cl.primerplayer.notificaciones.application.port.out.EnvioCorreo;
import cl.primerplayer.notificaciones.infrastructure.CorreoEnviadoRepositorio;
import cl.primerplayer.shared.api.FechasTienda;
import cl.primerplayer.ventas.events.PedidoConfirmado;
import cl.primerplayer.ventas.events.PedidoListoRetiro;
import lombok.extern.slf4j.Slf4j;

/**
 * Escucha los eventos DESPUÉS del commit, en otro hilo y sin transacción: el SMTP nunca retiene una conexión de base.
 * Spring Modulith guarda cada publicación en el outbox y la reintenta si el envío falla o la app se reinicia.
 * Los logs nunca incluyen tokens (regla 10).
 * <p>Los enlaces de verificación y recuperación se emiten aquí, después de reservar el envío y justo antes del SMTP
 * ({@link EnlacesCuentaUseCase}, con su propia transacción corta): los eventos del outbox no llevan tokens.
 */
@Service
@Slf4j
class CorreosService {

    private final EnvioCorreo correo;
    private final CorreoEnviadoRepositorio enviados;
    private final ConsultarUsuarioUseCase usuarios;
    private final EnlacesCuentaUseCase enlaces;
    private final Clock clock;
    private final String urlFront;

    CorreosService(EnvioCorreo correo, CorreoEnviadoRepositorio enviados, ConsultarUsuarioUseCase usuarios,
                   EnlacesCuentaUseCase enlaces, Clock clock,
                   @Value("${pp.notificaciones.url-front:http://localhost:4200}") String urlFront) {
        this.correo = correo;
        this.enviados = enviados;
        this.usuarios = usuarios;
        this.enlaces = enlaces;
        this.clock = clock;
        this.urlFront = urlFront.replaceAll("/+$", "");
    }

    @Async
    @TransactionalEventListener
    void alRegistrarse(UsuarioRegistrado e) {
        verificacion(e.usuarioId(), e.email(), e.nombre(), e.solicitudId(), "¡Bienvenido a Primer Player!");
    }

    @Async
    @TransactionalEventListener
    void alPedirVerificacion(VerificacionSolicitada e) {
        verificacion(e.usuarioId(), e.email(), e.nombre(), e.solicitudId(), "Verifica tu correo");
    }

    @Async
    @TransactionalEventListener
    void alPedirRecuperacion(RecuperacionSolicitada e) {
        enviarConEnlace(e.email(), "recuperacion-password", e.solicitudId(), "Restablece tu contraseña · Primer Player",
                () -> enlaces.emitirRecuperacion(e.usuarioId()), t -> """
                Hola %s:

                Recibimos una solicitud para restablecer la contraseña de tu cuenta en Primer Player.
                Para elegir una nueva, abre este enlace (vence en %s):

                %s

                Si no fuiste tú, ignora este correo: tu contraseña no cambiará.
                """.formatted(e.nombre(), vigencia(t.vigencia()), enlace("/cuenta/restablecer", t.valor())));
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
    void alListoParaRetiro(PedidoListoRetiro e) {
        UsuarioResumen cliente = usuarios.buscarPorIds(List.of(e.clienteId())).get(e.clienteId());
        if (cliente == null) {
            return;
        }
        String items = e.items().stream()
                .map(i -> "- %s × %d".formatted(i.nombre(), i.cantidad()))
                .collect(java.util.stream.Collectors.joining("\n"));
        enviarUnaVez(cliente.email(), "pedido-listo-retiro", e.codigo(),
                "Tu pedido " + e.codigo() + " está listo para retirar en tienda", """
                Hola %s:

                Tu pedido %s está listo para retirar en tienda.

                Ítems:
                %s

                Dirección de retiro: Av. Portales Ote. 1126, San Bernardo
                Preséntate con este código al llegar a la tienda.
                Código: %s
                """.formatted(cliente.nombre(), e.codigo(), items, e.codigo()));
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

    /**
     * Aviso a cada ADMIN: un vendedor creó un producto o le cambió el precio y espera aprobación. Uno por destinatario,
     * revisión y precio propuesto: si el vendedor vuelve a proponer otro precio en la misma revisión, llega otro aviso.
     */
    @Async
    @TransactionalEventListener
    void alQuedarProductoEnRevision(ProductoEnRevision e) {
        List<UsuarioResumen> admins = usuarios.administradoresActivos();
        if (admins.isEmpty()) {
            log.warn("Producto {} espera aprobación y no hay administradores activos a quienes avisar", e.productoId());
            return;
        }
        String vendedor = Optional.ofNullable(usuarios.buscarPorIds(List.of(e.solicitadoPor())).get(e.solicitadoPor()))
                .map(UsuarioResumen::nombre).orElse("Un vendedor");
        String referencia = "revision-" + e.revisionId() + "-precio-" + e.precioPropuesto();
        String asunto = (e.productoNuevo() ? "Producto nuevo por aprobar: " : "Cambio de precio por aprobar: ")
                + e.productoNombre();
        String detalle = e.productoNuevo()
                ? "%s agregó un producto nuevo que no se publicará hasta que lo apruebes:%n%n  %s%n  Precio: %s"
                        .formatted(vendedor, e.productoNombre(), clp(e.precioPropuesto()))
                : ("%s propuso cambiar el precio de un producto. La tienda sigue vendiéndolo al precio actual hasta "
                        + "que lo apruebes:%n%n  %s%n  Precio actual: %s%n  Precio propuesto: %s")
                        .formatted(vendedor, e.productoNombre(), clp(e.precioActual()), clp(e.precioPropuesto()));
        for (UsuarioResumen admin : admins) {
            enviarUnaVez(admin.email(), "producto-en-revision", referencia, asunto + " · Primer Player", """
                    Hola %s:

                    %s

                    Revísalo y apruébalo lo antes posible en el panel:

                    %s/admin/aprobaciones
                    """.formatted(admin.nombre(), detalle, urlFront));
        }
    }

    /** {@code $4.990}: CLP sin decimales con separador de miles chileno. */
    static String clp(long monto) {
        return String.format(java.util.Locale.forLanguageTag("es-CL"), "$%,d", monto);
    }

    // ---------- envío ----------

    private void verificacion(long usuarioId, String email, String nombre, UUID solicitudId, String asunto) {
        enviarConEnlace(email, "verificacion-cuenta", solicitudId, asunto + " · Primer Player",
                () -> enlaces.emitirVerificacion(usuarioId), t -> """
                Hola %s:

                Para activar las compras en Primer Player, confirma tu correo abriendo este enlace (vence en %s):

                %s

                Si no creaste una cuenta, ignora este correo.
                """.formatted(nombre, vigencia(t.vigencia()), enlace("/cuenta/verificar", t.valor())));
    }

    /**
     * Correo con enlace de un solo uso. El token se emite después de reservar el envío: un reintento del outbox de un
     * correo que ya salió no genera enlaces nuevos. Si ya no corresponde (cuenta verificada o inactiva) no se envía y
     * la reserva queda, así no se reintenta.
     */
    private void enviarConEnlace(String para, String plantilla, UUID solicitudId, String asunto,
                                 Supplier<Optional<TokenEmitido>> emitir, Function<TokenEmitido, String> texto) {
        enviarUnaVez(para, plantilla, "solicitud-" + solicitudId, asunto, () -> emitir.get().map(texto));
    }

    private void enviarUnaVez(String para, String plantilla, String referencia, String asunto, String texto) {
        enviarUnaVez(para, plantilla, referencia, asunto, () -> Optional.of(texto));
    }

    /**
     * Registra primero (UNIQUE plantilla + referencia + destino): si ya estaba registrado, este correo ya salió y no
     * se repite. Si el envío falla, se borra el registro y la excepción hace que el outbox lo reintente.
     *
     * @param texto se arma después de reservar; vacío = ya no corresponde enviarlo
     */
    private void enviarUnaVez(String para, String plantilla, String referencia, String asunto,
                              Supplier<Optional<String>> texto) {
        if (!enviados.reservar(para, plantilla, referencia, clock.instant())) {
            log.info("Correo {} ya enviado (ref {}): se omite", plantilla, referencia);
            return;
        }
        try {
            Optional<String> cuerpo = texto.get();
            if (cuerpo.isEmpty()) {
                log.info("Correo {} ya no corresponde (ref {}): se omite", plantilla, referencia);
                return;
            }
            correo.enviar(para, asunto, cuerpo.get());
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

    /** "24 horas", "1 hora", "30 minutos". */
    static String vigencia(Duration d) {
        long horas = d.toHours();
        if (horas >= 1 && d.toMinutesPart() == 0) {
            return horas == 1 ? "1 hora" : horas + " horas";
        }
        long minutos = d.toMinutes();
        return minutos == 1 ? "1 minuto" : minutos + " minutos";
    }
}
