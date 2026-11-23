package cl.primerplayer.ventas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import cl.primerplayer.TestcontainersConfiguration;
import cl.primerplayer.TokensDePrueba;
import cl.primerplayer.TokensDePrueba.UsuarioConToken;
import cl.primerplayer.shared.api.Rol;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class InscripcionesIntegracionTest {

    static final String ME = "/api/v1/me/inscripciones";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;

    UsuarioConToken cliente, otro, vendedor, admin;

    @BeforeEach
    void usuarios() {
        cliente = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
        otro = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
        vendedor = TokensDePrueba.crear(jdbc, encoder, Rol.VENDEDOR);
        admin = TokensDePrueba.crear(jdbc, encoder, Rol.ADMIN);
    }

    // ---------- helpers: datos directos en la base ----------

    long evento(String titulo, int enDias, int cupos) {
        return jdbc.queryForObject("""
                INSERT INTO evento (titulo, tipo, fecha_hora, lugar, precio, cupos_totales, descripcion, publicado)
                VALUES (?, 'league-cup', now() + make_interval(days => ?), 'Tienda', 7000, ?, 'x', true) RETURNING id
                """, Long.class, titulo, enDias, cupos);
    }

    /** Pedido + inscripción con snapshot del evento; suma los cupos si la inscripción queda vigente. */
    record Creada(long inscripcionId, String codigo) {}

    Creada inscribir(UsuarioConToken quien, long eventoId, int cantidad, String estado, String canal) {
        String codigo = "TI" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String estadoPedido = "RESERVADA".equals(estado) ? "PENDIENTE_PAGO" : "PAGADO";
        Long pedidoId = jdbc.queryForObject("""
                INSERT INTO pedido (codigo, cliente_id, vendedor_id, canal, estado, total)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, codigo, quien.id(), "tienda".equals(canal) ? vendedor.id() : null, canal, estadoPedido,
                7000L * cantidad);
        Long id = jdbc.queryForObject("""
                INSERT INTO inscripcion (evento_id, cliente_id, pedido_id, cantidad, estado, titulo_snapshot,
                                         fecha_snapshot, lugar_snapshot, precio_snapshot)
                SELECT id, ?, ?, ?, ?, titulo, fecha_hora, lugar, precio FROM evento WHERE id = ? RETURNING id
                """, Long.class, quien.id(), pedidoId, cantidad, estado, eventoId);
        if ("RESERVADA".equals(estado) || "CONFIRMADA".equals(estado)) {
            jdbc.update("UPDATE evento SET cupos_ocupados = cupos_ocupados + ? WHERE id = ?", cantidad, eventoId);
        }
        return new Creada(id, codigo);
    }

    Creada confirmada(UsuarioConToken quien, long eventoId, int cantidad) {
        return inscribir(quien, eventoId, cantidad, "CONFIRMADA", "web");
    }

    int ocupados(long eventoId) {
        return jdbc.queryForObject("SELECT cupos_ocupados FROM evento WHERE id = ?", Integer.class, eventoId);
    }

    ResultActions como(UsuarioConToken u, MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(u == null ? req : req.header(HttpHeaders.AUTHORIZATION, u.bearer()));
    }

    ResultActions cancelar(UsuarioConToken u, long id) throws Exception {
        return como(u, post(ME + "/" + id + "/cancelacion"));
    }

    // ---------- mis inscripciones ----------

    @Test
    void misInscripciones_proximasPrimeroYLuegoPasadas_soloLasPropias() throws Exception {
        long lejana = evento("Lejana", 30, 10);
        long cercana = evento("Cercana", 3, 10);
        long pasada = evento("Pasada", -5, 10);
        Creada c1 = confirmada(cliente, lejana, 2);
        Creada c2 = confirmada(cliente, cercana, 1);
        Creada c3 = confirmada(cliente, pasada, 1);
        confirmada(otro, cercana, 1);

        como(cliente, get(ME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].id", contains((int) c2.inscripcionId(), (int) c1.inscripcionId(), (int) c3.inscripcionId())))
                .andExpect(jsonPath("$[1].eventTitle").value("Lejana"))
                .andExpect(jsonPath("$[1].eventType").value("league-cup"))
                .andExpect(jsonPath("$[1].quantity").value(2))
                .andExpect(jsonPath("$[1].unitPrice").value(7000))
                .andExpect(jsonPath("$[1].total").value(14000))
                .andExpect(jsonPath("$[1].status").value("CONFIRMADA"))
                .andExpect(jsonPath("$[1].orderCode").value(c1.codigo()))
                .andExpect(jsonPath("$[1].eventDate").exists())
                .andExpect(jsonPath("$[1].cancellable").value(true))
                .andExpect(jsonPath("$[2].cancellable").value(false));   // el evento ya pasó
    }

    @Test
    void misInscripciones_elSnapshotNoCambiaSiLaTiendaEditaElEvento() throws Exception {
        long e = evento("Título original", 10, 10);
        confirmada(cliente, e, 1);
        jdbc.update("UPDATE evento SET titulo = 'Título nuevo', precio = 9000 WHERE id = ?", e);

        como(cliente, get(ME))
                .andExpect(jsonPath("$[0].eventTitle").value("Título original"))
                .andExpect(jsonPath("$[0].unitPrice").value(7000));
    }

    // ---------- cancelación ----------

    @Test
    void cancelar_liberaCuposYNoSePuedeRepetir() throws Exception {
        long e = evento("Torneo", 10, 10);
        Creada c = confirmada(cliente, e, 3);
        assertThat(ocupados(e)).isEqualTo(3);

        cancelar(cliente, c.inscripcionId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"))
                .andExpect(jsonPath("$.cancelledAt").exists())
                .andExpect(jsonPath("$.cancellable").value(false))
                .andExpect(jsonPath("$.orderCode").value(c.codigo()));
        assertThat(ocupados(e)).isZero();
        // El pedido sigue pagado: el reembolso lo gestiona la tienda (decisión de negocio pendiente)
        assertThat(jdbc.queryForObject("SELECT estado FROM pedido WHERE codigo = ?", String.class, c.codigo()))
                .isEqualTo("PAGADO");

        cancelar(cliente, c.inscripcionId())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("registration-not-cancellable"));
        assertThat(ocupados(e)).isZero();
    }

    @Test
    void cancelar_casosNoPermitidos() throws Exception {
        long futuro = evento("Futuro", 10, 10);
        long pasado = evento("Pasado", -1, 10);
        Creada reservada = inscribir(cliente, futuro, 1, "RESERVADA", "web");
        Creada vieja = confirmada(cliente, pasado, 1);
        Creada ajena = confirmada(otro, futuro, 1);

        cancelar(cliente, reservada.inscripcionId())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("registration-not-cancellable"));
        cancelar(cliente, vieja.inscripcionId())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("event-past"));
        cancelar(cliente, ajena.inscripcionId())
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("item-unavailable"));
        cancelar(cliente, 999_999_999L)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("item-unavailable"));
        assertThat(ocupados(futuro)).isEqualTo(2);   // nada se liberó
    }

    @Test
    void cancelar_concurrente_liberaLosCuposUnaSolaVez() throws Exception {
        long e = evento("Concurrido", 10, 10);
        Creada c = confirmada(cliente, e, 2);
        int hilos = 6;
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            resultados.add(pool.submit(() -> {
                largada.await();
                return cancelar(cliente, c.inscripcionId()).andReturn().getResponse().getStatus();
            }));
        }
        largada.countDown();
        List<Integer> codigos = new ArrayList<>();
        for (Future<Integer> r : resultados) {
            codigos.add(r.get());
        }
        pool.shutdown();

        assertThat(codigos).filteredOn(x -> x == 200).hasSize(1);
        assertThat(codigos).filteredOn(x -> x != 200).allMatch(x -> x == 409);
        assertThat(ocupados(e)).isZero();
    }

    // ---------- inscritos (tienda) ----------

    @Test
    void inscritos_listaConClienteCodigoYCanal() throws Exception {
        long e = evento("League Cup", 10, 20);
        Creada web = confirmada(cliente, e, 2);
        Creada mostrador = inscribir(otro, e, 1, "CONFIRMADA", "tienda");
        Creada pendiente = inscribir(otro, e, 1, "RESERVADA", "web");
        Creada cancelada = confirmada(cliente, e, 1);
        cancelar(cliente, cancelada.inscripcionId()).andExpect(status().isOk());

        como(vendedor, get("/api/v1/admin/eventos/" + e + "/inscritos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value((int) e))
                .andExpect(jsonPath("$.confirmedSeats").value(3))
                .andExpect(jsonPath("$.reservedSeats").value(1))
                .andExpect(jsonPath("$.registrations", hasSize(4)))
                .andExpect(jsonPath("$.registrations[*].id", contains((int) web.inscripcionId(),
                        (int) mostrador.inscripcionId(), (int) pendiente.inscripcionId(), (int) cancelada.inscripcionId())))
                .andExpect(jsonPath("$.registrations[0].customerName").value("Prueba CLIENTE"))
                .andExpect(jsonPath("$.registrations[0].email").value(cliente.email()))
                .andExpect(jsonPath("$.registrations[0].orderCode").value(web.codigo()))
                .andExpect(jsonPath("$.registrations[0].channel").value("web"))
                .andExpect(jsonPath("$.registrations[1].channel").value("tienda"))
                .andExpect(jsonPath("$.registrations[3].status").value("CANCELADA"));
    }

    @Test
    void inscritos_eventoInexistenteOEliminado_404() throws Exception {
        long eliminado = evento("Eliminado", 10, 10);
        jdbc.update("UPDATE evento SET eliminado_en = now() WHERE id = ?", eliminado);
        como(vendedor, get("/api/v1/admin/eventos/" + eliminado + "/inscritos"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("item-unavailable"));
        como(vendedor, get("/api/v1/admin/eventos/999999999/inscritos"))
                .andExpect(status().isNotFound());
    }

    // ---------- seguridad: rol × endpoint ----------

    @Test
    void seguridad() throws Exception {
        long e = evento("Seguridad", 10, 10);
        Creada c = confirmada(cliente, e, 1);

        como(null, get(ME)).andExpect(status().isUnauthorized());
        como(vendedor, get(ME)).andExpect(status().isOk());   // el personal también se inscribe
        como(admin, get(ME)).andExpect(status().isOk());
        como(cliente, get(ME)).andExpect(status().isOk());

        cancelar(null, c.inscripcionId()).andExpect(status().isUnauthorized());
        cancelar(vendedor, c.inscripcionId()).andExpect(status().isNotFound());   // ajena

        String inscritos = "/api/v1/admin/eventos/" + e + "/inscritos";
        como(null, get(inscritos)).andExpect(status().isUnauthorized());
        como(cliente, get(inscritos)).andExpect(status().isForbidden());
        como(vendedor, get(inscritos)).andExpect(status().isOk());
        como(admin, get(inscritos)).andExpect(status().isOk());
    }
}
