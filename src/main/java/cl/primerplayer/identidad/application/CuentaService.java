package cl.primerplayer.identidad.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.identidad.api.CuentaUseCase;
import cl.primerplayer.identidad.api.CodigoInvalidoException;
import cl.primerplayer.identidad.api.DemasiadosIntentosException;
import cl.primerplayer.identidad.api.YaVerificadoException;
import cl.primerplayer.identidad.application.port.out.CuentaRepositorio;
import cl.primerplayer.identidad.application.port.out.CuentaRepositorio.FilaCuenta;
import cl.primerplayer.identidad.application.port.out.HasherPassword;
import cl.primerplayer.identidad.application.port.out.UsuarioRepositorio;
import cl.primerplayer.identidad.domain.CodigoVerificacion;
import cl.primerplayer.identidad.domain.TokenUnUso;
import cl.primerplayer.identidad.domain.Usuario;
import cl.primerplayer.identidad.events.RecuperacionSolicitada;
import cl.primerplayer.identidad.events.VerificacionSolicitada;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import cl.primerplayer.shared.api.ValidacionException;

@Service
class CuentaService implements CuentaUseCase {

    static final int MIN_PASSWORD = 8;
    /**
     * Tope de códigos errados por cuenta y tipo en una hora, sumando todos los códigos emitidos. Sin él, pedir códigos
     * nuevos daría 5 intentos más cada vez; con él, adivinar un código de 6 dígitos es 1 en 100.000 por hora.
     */
    static final int MAX_INTENTOS_POR_HORA = 10;
    static final int MAX_BYTES_PASSWORD = 72;   // BCrypt ignora lo que pasa de 72 bytes

    private final CuentaRepositorio cuentas;
    private final UsuarioRepositorio usuarios;
    private final HasherPassword hasher;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    CuentaService(CuentaRepositorio cuentas, UsuarioRepositorio usuarios, HasherPassword hasher,
                  ApplicationEventPublisher eventos, Clock clock) {
        this.cuentas = cuentas;
        this.usuarios = usuarios;
        this.hasher = hasher;
        this.eventos = eventos;
        this.clock = clock;
    }

    // ---------- perfil ----------

    @Override
    @Transactional(readOnly = true)
    public Perfil perfil(long usuarioId) {
        return aPerfil(cuenta(usuarioId));
    }

    @Override
    @Transactional
    public Perfil actualizarPerfil(long usuarioId, String nombre, String telefono, String rut) {
        cuenta(usuarioId);
        String n = nombre == null ? "" : nombre.strip();
        if (n.isEmpty() || n.length() > 120) {
            throw new ValidacionException("nombre", "El nombre es obligatorio (máx. 120 caracteres).");
        }
        cuentas.actualizarPerfil(usuarioId, n, telefono(telefono), rut(rut), clock.instant());
        return aPerfil(cuenta(usuarioId));
    }

    // ---------- contraseña ----------

    @Override
    @Transactional
    public void cambiarPassword(long usuarioId, String actual, String nueva) {
        FilaCuenta c = cuenta(usuarioId);
        if (actual == null || c.passwordHash() == null || !hasher.coincide(actual, c.passwordHash())) {
            throw new ValidacionException("actual", "La contraseña actual no es correcta.");
        }
        validarPassword("nueva", nueva);
        Instant ahora = clock.instant();
        cuentas.cambiarPassword(usuarioId, hasher.hashear(nueva), ahora);
        cuentas.revocarSesiones(usuarioId, ahora);   // las otras sesiones deben volver a entrar
    }

    @Override
    @Transactional
    public void solicitarRecuperacion(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        // Siempre "acepta": no revela si el correo existe. Solo cuentas que pueden iniciar sesión reciben el enlace.
        usuarios.buscarPorEmail(Usuario.normalizarEmail(email)).filter(Usuario::puedeIniciarSesion).ifPresent(u -> {
            // el enlace se emite al enviar el correo (EnlacesCuentaUseCase): el outbox no guarda el token
            eventos.publishEvent(new RecuperacionSolicitada(u.id(), u.email(), u.nombre(), UUID.randomUUID()));
        });
    }

    @Override
    @Transactional(noRollbackFor = CodigoInvalidoException.class)   // el intento errado debe quedar contado
    public void restablecerPassword(String email, String codigo, String nueva) {
        validarPassword("password", nueva);
        Instant ahora = clock.instant();
        long usuarioId = consumir(email, codigo, TokenUnUso.Tipo.RECUPERACION, ahora);
        cuentas.cambiarPassword(usuarioId, hasher.hashear(nueva), ahora);
        cuentas.revocarSesiones(usuarioId, ahora);
        cuentas.marcarVerificado(usuarioId, ahora);   // recibir el correo prueba que el correo es suyo
    }

    // ---------- verificación ----------

    @Override
    @Transactional(noRollbackFor = CodigoInvalidoException.class)   // el intento errado debe quedar contado
    public void verificarEmail(String email, String codigo) {
        Instant ahora = clock.instant();
        cuentas.marcarVerificado(consumir(email, codigo, TokenUnUso.Tipo.VERIFICACION, ahora), ahora);
    }

    @Override
    @Transactional
    public void reenviarVerificacion(long usuarioId) {
        FilaCuenta c = cuenta(usuarioId);
        if (c.emailVerificadoEn() != null) {
            throw new YaVerificadoException();
        }
        eventos.publishEvent(new VerificacionSolicitada(usuarioId, c.email(), c.nombre(), UUID.randomUUID()));
    }

    // ---------- reglas ----------

    /**
     * Usa el código pendiente más reciente de la cuenta. Errar suma un intento: al 5º el código vence, y con 10 errados
     * en una hora (entre todos los códigos) la cuenta queda bloqueada para este tipo hasta que pase la hora.
     *
     * @return el usuario dueño del código
     */
    private long consumir(String email, String codigo, TokenUnUso.Tipo tipo, Instant ahora) {
        String limpio = CodigoVerificacion.normalizar(codigo);
        if (limpio == null || email == null || email.isBlank()) {
            throw new CodigoInvalidoException();
        }
        long usuarioId = cuentas.idPorEmail(Usuario.normalizarEmail(email)).orElseThrow(CodigoInvalidoException::new);
        if (cuentas.intentosFallidosDesde(usuarioId, tipo, ahora.minus(Duration.ofHours(1))) >= MAX_INTENTOS_POR_HORA) {
            throw new DemasiadosIntentosException();
        }
        var pendiente = cuentas.bloquearCodigoPendiente(usuarioId, tipo, ahora).orElseThrow(CodigoInvalidoException::new);
        if (!CodigoVerificacion.coincide(pendiente.id(), limpio, pendiente.hash())) {
            cuentas.registrarIntentoFallido(pendiente.id(), CodigoVerificacion.MAX_INTENTOS, ahora);
            throw new CodigoInvalidoException();
        }
        cuentas.marcarUsado(pendiente.id(), ahora);
        return usuarioId;
    }

    static void validarPassword(String campo, String password) {
        if (password == null || password.length() < MIN_PASSWORD
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_PASSWORD) {
            throw new ValidacionException(campo, "La contraseña debe tener entre 8 y 72 caracteres.");
        }
    }

    /** Dígitos, espacios, guiones y un + inicial; entre 8 y 15 dígitos. Vacío = sin teléfono. */
    static String telefono(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String t = valor.strip();
        int digitos = t.replaceAll("\\D", "").length();
        if (!t.matches("\\+?[0-9 \\-]+") || digitos < 8 || digitos > 15 || t.length() > 20) {
            throw new ValidacionException("telefono", "Teléfono inválido. Ejemplo: +56 9 1234 5678.");
        }
        return t;
    }

    /** Normaliza a {@code 12345678-K} y valida el dígito verificador (módulo 11). Vacío = sin RUT. */
    static String rut(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.replace(".", "").replace(" ", "").toUpperCase();
        if (!limpio.matches("\\d{7,8}-?[\\dK]")) {
            throw new ValidacionException("rut", "RUT inválido. Ejemplo: 12.345.678-5.");
        }
        String cuerpo = limpio.replace("-", "").substring(0, limpio.replace("-", "").length() - 1);
        char dv = limpio.charAt(limpio.length() - 1);
        int suma = 0;
        int factor = 2;
        for (int i = cuerpo.length() - 1; i >= 0; i--) {
            suma += (cuerpo.charAt(i) - '0') * factor;
            factor = factor == 7 ? 2 : factor + 1;
        }
        int r = 11 - (suma % 11);
        char esperado = r == 11 ? '0' : r == 10 ? 'K' : (char) ('0' + r);
        if (dv != esperado) {
            throw new ValidacionException("rut", "El dígito verificador del RUT no es correcto.");
        }
        return cuerpo + "-" + dv;
    }

    private FilaCuenta cuenta(long usuarioId) {
        return cuentas.porId(usuarioId).filter(FilaCuenta::activo)
                .orElseThrow(() -> new RecursoNoEncontradoException("La cuenta no existe."));
    }

    private static Perfil aPerfil(FilaCuenta c) {
        return new Perfil(c.id(), c.email(), c.nombre(), c.rol(), c.telefono(), c.rut(), c.creadoEn(),
                c.emailVerificadoEn() != null, c.cambioPasswordPendiente());
    }
}
