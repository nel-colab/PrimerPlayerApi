package cl.primerplayer.identidad.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.identidad.api.AccionSobreSiMismoException;
import cl.primerplayer.identidad.api.AdminUsuariosUseCase;
import cl.primerplayer.identidad.api.UltimoAdminException;
import cl.primerplayer.identidad.api.YaVerificadoException;
import cl.primerplayer.identidad.application.port.out.AdminUsuariosRepositorio;
import cl.primerplayer.identidad.application.port.out.CuentaRepositorio;
import cl.primerplayer.identidad.events.RecuperacionSolicitada;
import cl.primerplayer.identidad.events.UsuarioAdministrado;
import cl.primerplayer.identidad.events.VerificacionSolicitada;
import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import cl.primerplayer.shared.api.Rol;
import cl.primerplayer.shared.api.ValidacionException;

@Service
class AdminUsuariosService implements AdminUsuariosUseCase {

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_MOTIVO = 300;

    private final AdminUsuariosRepositorio admin;
    private final CuentaRepositorio cuentas;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    AdminUsuariosService(AdminUsuariosRepositorio admin, CuentaRepositorio cuentas, ApplicationEventPublisher eventos,
                         Clock clock) {
        this.admin = admin;
        this.cuentas = cuentas;
        this.eventos = eventos;
        this.clock = clock;
    }

    // ---------- lecturas ----------

    @Override
    @Transactional(readOnly = true)
    public Pagina<UsuarioAdmin> buscar(Filtro f) {
        if (f.page() < 0 || f.size() < 1 || f.size() > MAX_PAGE_SIZE) {
            throw new ValidacionException("size", "page >= 0 y size entre 1 y " + MAX_PAGE_SIZE + ".");
        }
        Instant ahora = clock.instant();
        return Pagina.de(admin.buscar(f, ahora), f.page(), f.size(), admin.contar(f, ahora));
    }

    @Override
    @Transactional(readOnly = true)
    public Indicadores indicadores() {
        return admin.indicadores(clock.instant());
    }

    // ---------- acciones ----------

    @Override
    @Transactional
    public UsuarioAdmin cambiarRol(long actorId, long usuarioId, Rol rol) {
        noSobreSiMismo(actorId, usuarioId);
        admin.bloquearAdmins();
        UsuarioAdmin u = cuenta(usuarioId);
        if (u.rol() == rol) {
            return u;
        }
        if (u.rol() == Rol.ADMIN) {
            quedaOtroAdmin(u);
        }
        admin.cambiarRol(usuarioId, rol, clock.instant());
        return auditar(actorId, u, "ROL_CAMBIADO", Map.of("rol", u.rol().name()), Map.of("rol", rol.name()));
    }

    @Override
    @Transactional
    public UsuarioAdmin banear(long actorId, long usuarioId, String motivo, Instant hasta) {
        noSobreSiMismo(actorId, usuarioId);
        String m = motivo(motivo);
        Instant ahora = clock.instant();
        if (hasta != null && !hasta.isAfter(ahora)) {
            throw new ValidacionException("hasta", "La fecha de término debe ser futura (o vacía para un baneo permanente).");
        }
        admin.bloquearAdmins();
        UsuarioAdmin u = cuenta(usuarioId);
        if (u.rol() == Rol.ADMIN) {
            quedaOtroAdmin(u);
        }
        admin.banear(usuarioId, m, hasta, ahora);
        cuentas.revocarSesiones(usuarioId, ahora);   // fuera de inmediato (el access token vigente expira en ≤ 15 min)
        return auditar(actorId, u, "USUARIO_BANEADO", Map.of(), mapa("motivo", m, "hasta", hasta == null ? null : hasta.toString()));
    }

    @Override
    @Transactional
    public UsuarioAdmin quitarBaneo(long actorId, long usuarioId) {
        UsuarioAdmin u = cuenta(usuarioId);
        admin.quitarBaneo(usuarioId, clock.instant());
        return auditar(actorId, u, "BANEO_QUITADO", u.baneo() == null ? Map.of() : Map.of("motivo", u.baneo().motivo()), Map.of());
    }

    @Override
    @Transactional
    public UsuarioAdmin ponerEnListaNegra(long actorId, long usuarioId, String motivo) {
        noSobreSiMismo(actorId, usuarioId);
        String m = motivo(motivo);
        UsuarioAdmin u = cuenta(usuarioId);
        admin.ponerEnListaNegra(usuarioId, m, clock.instant());
        return auditar(actorId, u, "LISTA_NEGRA_AGREGADO", Map.of(), Map.of("motivo", m));
    }

    @Override
    @Transactional
    public UsuarioAdmin quitarDeListaNegra(long actorId, long usuarioId) {
        UsuarioAdmin u = cuenta(usuarioId);
        admin.quitarDeListaNegra(usuarioId, clock.instant());
        return auditar(actorId, u, "LISTA_NEGRA_QUITADO",
                u.listaNegra() == null ? Map.of() : Map.of("motivo", u.listaNegra().motivo()), Map.of());
    }

    @Override
    @Transactional
    public UsuarioAdmin solicitarCambioPassword(long actorId, long usuarioId) {
        UsuarioAdmin u = cuenta(usuarioId);
        Instant ahora = clock.instant();
        admin.marcarCambioPassword(usuarioId, ahora);
        eventos.publishEvent(new RecuperacionSolicitada(usuarioId, u.email(), u.nombre(), UUID.randomUUID()));
        return auditar(actorId, u, "CAMBIO_PASSWORD_SOLICITADO", Map.of(), Map.of("cambioPasswordPendiente", "true"));
    }

    @Override
    @Transactional
    public UsuarioAdmin reenviarVerificacion(long actorId, long usuarioId) {
        UsuarioAdmin u = cuenta(usuarioId);
        if (u.emailVerificado()) {
            throw new YaVerificadoException();
        }
        eventos.publishEvent(new VerificacionSolicitada(usuarioId, u.email(), u.nombre(), UUID.randomUUID()));
        return auditar(actorId, u, "VERIFICACION_REENVIADA", Map.of(), Map.of());
    }

    @Override
    @Transactional
    public UsuarioAdmin cerrarSesiones(long actorId, long usuarioId) {
        noSobreSiMismo(actorId, usuarioId);
        UsuarioAdmin u = cuenta(usuarioId);
        int revocadas = cuentas.revocarSesiones(usuarioId, clock.instant());
        return auditar(actorId, u, "SESIONES_CERRADAS", Map.of(), Map.of("sesionesRevocadas", String.valueOf(revocadas)));
    }

    // ---------- reglas ----------

    private UsuarioAdmin cuenta(long usuarioId) {
        return admin.bloquear(usuarioId, clock.instant())
                .orElseThrow(() -> new RecursoNoEncontradoException("El usuario no existe."));
    }

    private static void noSobreSiMismo(long actorId, long usuarioId) {
        if (actorId == usuarioId) {
            throw new AccionSobreSiMismoException();
        }
    }

    /** Llamar con {@code bloquearAdmins()} tomado: dos admins no se quitan el rol mutuamente a la vez. */
    private void quedaOtroAdmin(UsuarioAdmin u) {
        boolean cuentaComoActivo = u.baneo() == null;
        if (cuentaComoActivo && admin.adminsActivos(clock.instant()) <= 1) {
            throw new UltimoAdminException();
        }
    }

    private static String motivo(String motivo) {
        String m = motivo == null ? "" : motivo.strip();
        if (m.isEmpty() || m.length() > MAX_MOTIVO) {
            throw new ValidacionException("motivo", "El motivo es obligatorio (máx. " + MAX_MOTIVO + " caracteres).");
        }
        return m;
    }

    /** Publica la auditoría y devuelve la cuenta con su estado actualizado. */
    private UsuarioAdmin auditar(long actorId, UsuarioAdmin antes, String accion, Map<String, String> datosAntes,
                                 Map<String, String> datosDespues) {
        eventos.publishEvent(new UsuarioAdministrado(actorId, antes.id(), accion, json(datosAntes), json(datosDespues),
                clock.instant()));
        return admin.bloquear(antes.id(), clock.instant()).orElseThrow();
    }

    private static Map<String, String> mapa(String k1, String v1, String k2, String v2) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    private static String json(Map<String, String> m) {
        if (m.isEmpty()) {
            return null;
        }
        return m.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\": " + (e.getValue() == null ? "null" : texto(e.getValue())))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private static String texto(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> b.append(c < 0x20 ? String.format("\\u%04x", (int) c) : String.valueOf(c));
            }
        }
        return b.append('"').toString();
    }
}
