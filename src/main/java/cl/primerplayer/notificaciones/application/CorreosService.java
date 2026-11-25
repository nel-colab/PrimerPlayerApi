package cl.primerplayer.notificaciones.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import cl.primerplayer.catalogo.events.ProductoEnRevision;
import cl.primerplayer.clientes.events.AlertaStockDisparada;
import cl.primerplayer.identidad.api.ConsultarUsuarioUseCase;
import cl.primerplayer.identidad.api.EnlacesCuentaUseCase;
import cl.primerplayer.identidad.api.EnlacesCuentaUseCase.TokenEmitido;
import cl.primerplayer.identidad.api.UsuarioResumen;
import cl.primerplayer.identidad.events.RecuperacionSolicitada;
import cl.primerplayer.identidad.events.UsuarioRegistrado;
import cl.primerplayer.identidad.events.VerificacionSolicitada;
import cl.primerplayer.notificaciones.application.MensajeCorreo.Destacado;
import cl.primerplayer.notificaciones.application.MensajeCorreo.Detalle;
import cl.primerplayer.notificaciones.application.port.out.EnvioCorreo;
import cl.primerplayer.notificaciones.infrastructure.CorreoEnviadoRepositorio;
import cl.primerplayer.shared.api.FechasTienda;
import cl.primerplayer.ventas.events.PedidoConfirmado;
import cl.primerplayer.ventas.events.PedidoListoRetiro;
import lombok.extern.slf4j.Slf4j;

/**
 * Escucha los eventos DESPUÉS del commit, en otro hilo y sin transacción: el SMTP nunca retiene una conexión de base.
 * Spring Modulith guarda cada publicación en el outbox y la reintenta si el envío falla o la app se reinicia.
 * <p><b>Ningún correo lleva URLs, enlaces ni botones</b> (decisión de la tienda): todo se explica con texto, y verificar
 * el correo o restablecer la contraseña usan un código de 6 dígitos que la persona escribe en la web. El diseño (logo,
 * colores, centrado) está en la plantilla {@code templates/correo/mensaje.html}.
 * <p>Los códigos se emiten aquí, después de reservar el envío y justo antes del SMTP ({@link EnlacesCuentaUseCase}, con
 * su propia transacción corta): los eventos del outbox no llevan códigos. Los logs nunca los incluyen (regla 10).
 */
@Service
@Slf4j
class CorreosService {

    static final String DIRECCION_TIENDA = "Av. Portales Ote. 1126, San Bernardo";

    private final EnvioCorreo correo;
    private final CorreoEnviadoRepositorio enviados;
    private final ConsultarUsuarioUseCase usuarios;
    private final EnlacesCuentaUseCase enlaces;
    private final Clock clock;

    CorreosService(EnvioCorreo correo, CorreoEnviadoRepositorio enviados, ConsultarUsuarioUseCase usuarios,
                   EnlacesCuentaUseCase enlaces, Clock clock) {
        this.correo = correo;
        this.enviados = enviados;
        this.usuarios = usuarios;
        this.enlaces = enlaces;
        this.clock = clock;
    }

    // ---------- cuenta ----------

    @Async
    @TransactionalEventListener
    void alRegistrarse(UsuarioRegistrado e) {
        verificacion(e.usuarioId(), e.email(), e.nombre(), e.solicitudId(), true);
    }

    @Async
    @TransactionalEventListener
    void alPedirVerificacion(VerificacionSolicitada e) {
        verificacion(e.usuarioId(), e.email(), e.nombre(), e.solicitudId(), false);
    }

    @Async
    @TransactionalEventListener
    void alPedirRecuperacion(RecuperacionSolicitada e) {
        enviarConCodigo(e.email(), "recuperacion-password", e.solicitudId(),
                () -> enlaces.emitirRecuperacion(e.usuarioId()), c -> new MensajeCorreo(
                        "Tu código para restablecer la contraseña",
                        "Usa este código para crear una contraseña nueva. Vence en " + vigencia(c.vigencia()) + ".",
                        "Restablece tu contraseña",
                        saludo(e.nombre()),
                        List.of("Recibimos una solicitud para restablecer la contraseña de tu cuenta.",
                                "Usa este código para elegir una contraseña nueva:"),
                        new Destacado("Tu código", c.valor(), true, "Vence en " + vigencia(c.vigencia())),
                        List.of(),
                        List.of("Entra a la web de Primer Player y abre «Iniciar sesión».",
                                "Elige «¿Olvidaste tu contraseña?» y luego «Ya tengo un código».",
                                "Escribe tu correo, este código y tu contraseña nueva."),
                        "¿No fuiste tú? Ignora este correo: tu contraseña no cambiará. Nunca te pediremos este código "
                                + "por teléfono, WhatsApp ni redes sociales."));
    }

    private void verificacion(long usuarioId, String email, String nombre, UUID solicitudId, boolean bienvenida) {
        enviarConCodigo(email, "verificacion-cuenta", solicitudId, () -> enlaces.emitirVerificacion(usuarioId),
                c -> new MensajeCorreo(
                        bienvenida ? "¡Bienvenido! Confirma tu correo" : "Tu código para verificar el correo",
                        "Tu código de verificación vence en " + vigencia(c.vigencia()) + ".",
                        bienvenida ? "¡Bienvenido a Primer Player!" : "Verifica tu correo",
                        saludo(nombre),
                        bienvenida
                                ? List.of("Gracias por crear tu cuenta. Ya puedes explorar el catálogo y los eventos.",
                                          "Para comprar e inscribirte en torneos, confirma que este correo es tuyo con "
                                                  + "este código:")
                                : List.of("Para confirmar que este correo es tuyo, usa este código:"),
                        new Destacado("Tu código de verificación", c.valor(), true,
                                "Vence en " + vigencia(c.vigencia())),
                        List.of(),
                        List.of("Entra a la web de Primer Player con tu cuenta.",
                                "Ve a «Mi perfil» y elige «Verificar correo».",
                                "Escribe este código y listo."),
                        "Si no creaste una cuenta en Primer Player, ignora este correo. Nunca te pediremos este código "
                                + "por teléfono, WhatsApp ni redes sociales."));
    }

    // ---------- pedidos ----------

    @Async
    @TransactionalEventListener
    void alConfirmarPedido(PedidoConfirmado e) {
        UsuarioResumen cliente = usuarios.buscarPorIds(List.of(e.clienteId())).get(e.clienteId());
        if (cliente == null) {
            return;
        }
        enviarUnaVez(cliente.email(), "pedido-confirmado", e.codigo(), new MensajeCorreo(
                "Tu pedido " + e.codigo() + " está confirmado",
                "Recibimos tu pago. Te avisaremos cuando esté listo para retirar.",
                "¡Gracias por tu compra!",
                saludo(cliente.nombre()),
                List.of("Recibimos el pago de tu pedido. Lo estamos preparando y te enviaremos otro correo cuando "
                        + "esté listo para retirar en la tienda."),
                new Destacado("Total pagado", clp(e.total()), false, null),
                List.of(new Detalle("Pedido", e.codigo()),
                        new Detalle("Retiro en tienda", DIRECCION_TIENDA)),
                List.of(),
                "Puedes ver el detalle en «Mis pedidos», dentro de tu cuenta en la web de Primer Player."));
    }

    @Async
    @TransactionalEventListener
    void alListoParaRetiro(PedidoListoRetiro e) {
        UsuarioResumen cliente = usuarios.buscarPorIds(List.of(e.clienteId())).get(e.clienteId());
        if (cliente == null) {
            return;
        }
        List<Detalle> items = e.items().stream()
                .map(i -> new Detalle(i.nombre(), "× " + i.cantidad()))
                .toList();
        enviarUnaVez(cliente.email(), "pedido-listo-retiro", e.codigo(), new MensajeCorreo(
                "Tu pedido " + e.codigo() + " está listo para retirar",
                "Ya puedes pasar a buscarlo a la tienda.",
                "¡Tu pedido está listo!",
                saludo(cliente.nombre()),
                List.of("Ya puedes pasar a buscar tu pedido a la tienda.",
                        "Muestra este código al llegar:"),
                new Destacado("Código de retiro", e.codigo(), false, DIRECCION_TIENDA),
                items,
                List.of(),
                "Si va a retirar otra persona, compártele este código."));
    }

    // ---------- catálogo ----------

    @Async
    @TransactionalEventListener
    void alReponerseStock(AlertaStockDisparada e) {
        enviarUnaVez(e.email(), "alerta-stock",
                "producto-" + e.productoId() + "-" + LocalDate.now(clock.withZone(FechasTienda.ZONA)), new MensajeCorreo(
                        "Volvió el stock: " + e.productoNombre(),
                        "El producto que esperabas ya está disponible. Las unidades son limitadas.",
                        "¡Volvió el stock!",
                        saludo(e.nombre()),
                        List.of("El producto que estabas esperando ya está disponible de nuevo:"),
                        new Destacado("Disponible", e.productoNombre(), false, "Unidades limitadas"),
                        List.of(),
                        List.of(),
                        "Búscalo por su nombre en el catálogo de la web de Primer Player o pregunta por él en la "
                                + "tienda. Te avisamos porque activaste la alerta de stock de este producto."));
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
        for (UsuarioResumen admin : admins) {
            MensajeCorreo m = e.productoNuevo()
                    ? new MensajeCorreo(
                            "Producto nuevo por aprobar: " + e.productoNombre(),
                            vendedor + " agregó un producto que espera tu aprobación.",
                            "Producto nuevo por aprobar",
                            saludo(admin.nombre()),
                            List.of(vendedor + " agregó un producto nuevo. No se publicará en la tienda hasta que "
                                    + "lo apruebes."),
                            new Destacado("Precio", clp(e.precioPropuesto()), false, null),
                            List.of(new Detalle("Producto", e.productoNombre()),
                                    new Detalle("Solicitado por", vendedor)),
                            List.of(),
                            "Revísalo y apruébalo lo antes posible en el panel de administración, en "
                                    + "«Aprobación de productos».")
                    : new MensajeCorreo(
                            "Cambio de precio por aprobar: " + e.productoNombre(),
                            vendedor + " propuso un precio nuevo que espera tu aprobación.",
                            "Cambio de precio por aprobar",
                            saludo(admin.nombre()),
                            List.of(vendedor + " propuso cambiar el precio de un producto. La tienda lo sigue "
                                    + "vendiendo al precio actual hasta que lo apruebes."),
                            new Destacado("Precio propuesto", clp(e.precioPropuesto()), false,
                                    "Precio actual: " + clp(e.precioActual())),
                            List.of(new Detalle("Producto", e.productoNombre()),
                                    new Detalle("Solicitado por", vendedor)),
                            List.of(),
                            "Revísalo y apruébalo lo antes posible en el panel de administración, en "
                                    + "«Aprobación de productos».");
            enviarUnaVez(admin.email(), "producto-en-revision", referencia, m);
        }
    }

    // ---------- formato ----------

    private static String saludo(String nombre) {
        return nombre == null || nombre.isBlank() ? "¡Hola!" : "¡Hola, " + nombre.strip() + "!";
    }

    /** {@code $4.990}: CLP sin decimales con separador de miles chileno. */
    static String clp(Long monto) {
        return monto == null ? "—" : String.format(java.util.Locale.forLanguageTag("es-CL"), "$%,d", monto);
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

    // ---------- envío ----------

    /**
     * Correo con código de un solo uso. El código se emite después de reservar el envío: un reintento del outbox de un
     * correo que ya salió no genera códigos nuevos. Si ya no corresponde (cuenta verificada o inactiva) no se envía y
     * la reserva queda, así no se reintenta.
     */
    private void enviarConCodigo(String para, String plantilla, UUID solicitudId,
                                 Supplier<Optional<TokenEmitido>> emitir, Function<TokenEmitido, MensajeCorreo> mensaje) {
        enviarUnaVez(para, plantilla, "solicitud-" + solicitudId, () -> emitir.get().map(mensaje));
    }

    private void enviarUnaVez(String para, String plantilla, String referencia, MensajeCorreo mensaje) {
        enviarUnaVez(para, plantilla, referencia, () -> Optional.of(mensaje));
    }

    /**
     * Registra primero (UNIQUE plantilla + referencia + destino): si ya estaba registrado, este correo ya salió y no
     * se repite. Si el envío falla, se borra el registro y la excepción hace que el outbox lo reintente.
     *
     * @param mensaje se arma después de reservar; vacío = ya no corresponde enviarlo
     */
    private void enviarUnaVez(String para, String plantilla, String referencia, Supplier<Optional<MensajeCorreo>> mensaje) {
        if (!enviados.reservar(para, plantilla, referencia, clock.instant())) {
            log.info("Correo {} ya enviado (ref {}): se omite", plantilla, referencia);
            return;
        }
        try {
            Optional<MensajeCorreo> m = mensaje.get();
            if (m.isEmpty()) {
                log.info("Correo {} ya no corresponde (ref {}): se omite", plantilla, referencia);
                return;
            }
            correo.enviar(para, m.get());
            log.info("Correo {} enviado (ref {})", plantilla, referencia);
        } catch (RuntimeException ex) {
            enviados.liberar(para, plantilla, referencia);
            log.warn("No se pudo enviar el correo {} (ref {}): {}", plantilla, referencia, ex.getMessage());
            throw ex;
        }
    }
}
