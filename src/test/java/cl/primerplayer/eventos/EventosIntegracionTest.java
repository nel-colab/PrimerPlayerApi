package cl.primerplayer.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import cl.primerplayer.DiasDePrueba;
import cl.primerplayer.TestcontainersConfiguration;
import cl.primerplayer.TokensDePrueba;
import cl.primerplayer.TokensDePrueba.UsuarioConToken;
import cl.primerplayer.eventos.api.CuposInsuficientesException;
import cl.primerplayer.eventos.api.CuposOperaciones;
import cl.primerplayer.eventos.api.CuposOperaciones.LineaCupo;
import cl.primerplayer.shared.api.Rol;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class EventosIntegracionTest {

    static final String ADMIN = "/api/v1/admin/eventos";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired CuposOperaciones cupos;
    @Autowired TransactionTemplate tx;

    UsuarioConToken admin, vendedor, cliente;

    @BeforeEach
    void usuarios() {
        admin = TokensDePrueba.crear(jdbc, encoder, Rol.ADMIN);
        vendedor = TokensDePrueba.crear(jdbc, encoder, Rol.VENDEDOR);
        cliente = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
    }

    static String evento(String tipo, String fecha, String fin, Integer cupos, Integer limite, boolean publicado) {
        return """
            {"titulo":"League Challenge de Noviembre","tipo":"%s","fecha":"%s","fechaFin":%s,
             "lugar":"Av. Portales Ote. 1126, San Bernardo","cuposTotales":%s,"precio":5000,"limitePorUsuario":%s,
             "descripcion":"Pon a prueba tu mazo.","consideraciones":["Trae tu mazo preparado para jugar.","Llega con anticipación."],
             "imagen":"https://cdn.test/lc.webp","publicado":%s}
            """.formatted(tipo, fecha, fin == null ? "null" : "\"" + fin + "\"", cupos, limite, publicado);
    }

    private ResultActions como(UsuarioConToken u, MockHttpServletRequestBuilder r) throws Exception {
        return mvc.perform(r.header(HttpHeaders.AUTHORIZATION, u.bearer()));
    }

    private long crear(String body) throws Exception {
        String r = como(vendedor, post(ADMIN).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(r, "$.id")).longValue();
    }

    // ---------- mantenedor ----------

    @Test
    void crear_201ConFormaDeLaTarjetaYFechaEnHoraDeChile() throws Exception {
        LocalDate dia = DiasDePrueba.siguiente();
        como(vendedor, post(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content(evento("league-challenge", DiasDePrueba.a(dia, 15), null, 16, null, true)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.tipo").value("league-challenge"))
                .andExpect(jsonPath("$.fecha").value(org.hamcrest.Matchers.startsWith(DiasDePrueba.a(dia, 15))))
                .andExpect(jsonPath("$.cuposTotales").value(16))
                .andExpect(jsonPath("$.cuposDisponibles").value(16))
                .andExpect(jsonPath("$.consideraciones.length()").value(2))
                .andExpect(jsonPath("$.tiendaCerrada").value(false))
                .andExpect(jsonPath("$.imagen").value("https://cdn.test/lc.webp"));
    }

    @Test
    void flujo_actualizarConIfMatch_publicar_eliminar() throws Exception {
        LocalDate dia = DiasDePrueba.siguiente();
        long id = crear(evento("league-cup", DiasDePrueba.a(dia, 14), null, 20, 2, false));

        como(vendedor, put(ADMIN + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(evento("league-cup", DiasDePrueba.a(dia, 14), null, 24, 2, false)))
                .andExpect(status().isPreconditionRequired());
        como(vendedor, put(ADMIN + "/" + id).header(HttpHeaders.IF_MATCH, "\"0\"").contentType(MediaType.APPLICATION_JSON)
                .content(evento("league-cup", DiasDePrueba.a(dia, 14), null, 24, 2, false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cuposTotales").value(24))
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""));
        como(vendedor, put(ADMIN + "/" + id).header(HttpHeaders.IF_MATCH, "\"0\"").contentType(MediaType.APPLICATION_JSON)
                .content(evento("league-cup", DiasDePrueba.a(dia, 14), null, 24, 2, false)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("version-conflict"));
        como(vendedor, put(ADMIN + "/" + id).header(HttpHeaders.IF_MATCH, "\"1\"").contentType(MediaType.APPLICATION_JSON)
                .content(evento("league-cup", DiasDePrueba.a(dia, 14), null, 24, 4, false)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("limit-admin-only"));

        mvc.perform(get("/api/v1/eventos/" + id)).andExpect(status().isNotFound());   // no publicado
        como(vendedor, patch(ADMIN + "/" + id + "/publicacion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"publicado\":true}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/eventos/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.cuposOcupados").doesNotExist());

        como(vendedor, delete(ADMIN + "/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/eventos/" + id)).andExpect(status().isNotFound());
        como(vendedor, get(ADMIN + "/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void validaciones_400() throws Exception {
        LocalDate dia = DiasDePrueba.siguiente();
        como(vendedor, post(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content(evento("bingo", DiasDePrueba.a(dia, 15), null, 16, null, true)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("tipo"));
        como(vendedor, post(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content(evento("league-cup", "14/11/2026 15:00", null, 16, null, true)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("fecha"));
        como(vendedor, post(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content(evento("league-cup", DiasDePrueba.a(dia, 15), null, null, null, true)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("cuposTotales"));
        como(vendedor, post(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content(evento("cumpleanos", DiasDePrueba.a(dia, 15), null, null, null, true)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("fechaHoraFin"));
    }

    // ---------- agenda: la tienda cierra durante un cumpleaños ----------

    @Test
    void agenda_cumpleanosYEventosNoSeTraslapan() throws Exception {
        LocalDate dia = DiasDePrueba.siguiente();
        long torneo = crear(evento("league-challenge", DiasDePrueba.a(dia, 11), null, 16, null, true));   // 11-14 (3 h estimadas)

        // Cumpleaños 13-16 choca con el torneo (termina a las 14)
        como(vendedor, post(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content(evento("cumpleanos", DiasDePrueba.a(dia, 13), DiasDePrueba.a(dia, 16), null, null, true)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("slot-taken"));
        // 14-17 empieza justo cuando termina: permitido
        long cumple = crear(evento("cumpleanos", DiasDePrueba.a(dia, 14), DiasDePrueba.a(dia, 17), null, null, true));
        // Otro torneo a las 16 cae dentro del cumpleaños
        como(vendedor, post(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content(evento("prelanzamiento", DiasDePrueba.a(dia, 16), null, 12, null, true)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("slot-taken"));
        // Dos eventos normales sí pueden convivir
        crear(evento("lanzamiento", DiasDePrueba.a(dia, 11), null, 30, null, true));

        mvc.perform(get("/api/v1/agenda/ocupados").param("desde", dia.toString()).param("hasta", dia.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.tiendaCerrada == true)].tipo").value(containsInAnyOrder("cumpleanos")));

        // Al eliminar el cumpleaños el bloque se libera
        como(vendedor, delete(ADMIN + "/" + cumple)).andExpect(status().isNoContent());
        crear(evento("prelanzamiento", DiasDePrueba.a(dia, 16), null, 12, null, true));
        assertThat(torneo).isPositive();
    }

    @Test
    void agenda_laBaseRechazaDosCumpleanosTraslapados() {
        LocalDate dia = DiasDePrueba.siguiente();
        String sql = """
            INSERT INTO evento (titulo, tipo, fecha_hora, fecha_hora_fin, lugar, precio, descripcion)
            VALUES ('c', 'cumpleanos', ?::timestamp AT TIME ZONE 'America/Santiago',
                    ?::timestamp AT TIME ZONE 'America/Santiago', 'x', 0, 'x')""";
        jdbc.update(sql, DiasDePrueba.a(dia, 10), DiasDePrueba.a(dia, 13));
        assertThatThrownBy(() -> jdbc.update(sql, DiasDePrueba.a(dia, 12), DiasDePrueba.a(dia, 15)))
                .hasMessageContaining("ex_evento_cumpleanos_traslape");
    }

    // ---------- vista pública ----------

    @Test
    void publico_soloPublicadosYProximosPorFecha_cumpleanosComoTiendaCerrada() throws Exception {
        LocalDate dia = DiasDePrueba.siguiente();
        long visible = crear(evento("league-cup", DiasDePrueba.a(dia, 10), null, 20, null, true));
        long oculto = crear(evento("league-cup", DiasDePrueba.a(dia, 10), null, 20, null, false));
        long cumple = crear(evento("cumpleanos", DiasDePrueba.a(dia, 15), DiasDePrueba.a(dia, 18), null, null, true));

        mvc.perform(get("/api/v1/eventos").param("desde", dia.toString()).param("hasta", dia.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.contains((int) visible, (int) cumple)))
                .andExpect(jsonPath("$.content[1].tiendaCerrada").value(true))
                .andExpect(jsonPath("$.content[1].cuposDisponibles").doesNotExist())
                .andExpect(jsonPath("$.content[1].tipo").value("cumpleanos"));
        mvc.perform(get("/api/v1/eventos").param("tipo", "league-cup").param("desde", dia.toString()))
                .andExpect(jsonPath("$.content[*].id").value(not(hasItem((int) oculto))));
        // Sin "desde" no aparecen eventos pasados
        long pasado = jdbc.queryForObject("""
            INSERT INTO evento (titulo, tipo, fecha_hora, lugar, precio, cupos_totales, descripcion, publicado)
            VALUES ('Pasado', 'league-cup', now() - interval '2 days', 'x', 0, 10, 'x', TRUE) RETURNING id""", Long.class);
        mvc.perform(get("/api/v1/eventos").param("size", "100"))
                .andExpect(jsonPath("$.content[*].id").value(not(hasItem((int) pasado))));
    }

    // ---------- cupos (para checkout / pos) ----------

    private long eventoConCupos(int cupos) throws Exception {
        return crear(evento("league-challenge", DiasDePrueba.a(DiasDePrueba.siguiente(), 15), null, cupos, null, true));
    }

    @Test
    void cupos_exigenTransaccion_yValidanLlenoPasadoYCumpleanos() throws Exception {
        long id = eventoConCupos(2);
        assertThatThrownBy(() -> cupos.ocupar(List.of(new LineaCupo(id, 1))))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> cupos.ocupar(List.of(new LineaCupo(id, 3)))))
                .isInstanceOfSatisfying(CuposInsuficientesException.class, e -> assertThat(e.code()).isEqualTo("event-full"));

        jdbc.update("UPDATE evento SET fecha_hora = now() - interval '1 hour' WHERE id = ?", id);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> cupos.ocupar(List.of(new LineaCupo(id, 1)))))
                .isInstanceOfSatisfying(CuposInsuficientesException.class, e -> assertThat(e.code()).isEqualTo("event-past"));

        LocalDate dia = DiasDePrueba.siguiente();
        long cumple = crear(evento("cumpleanos", DiasDePrueba.a(dia, 15), DiasDePrueba.a(dia, 18), null, null, true));
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> cupos.ocupar(List.of(new LineaCupo(cumple, 1)))))
                .hasMessageContaining("no está disponible");
    }

    @Test
    void cupos_ocuparYLiberar_seReflejanEnLaTarjeta_yNoBajanDeLoVendido() throws Exception {
        long id = eventoConCupos(16);
        tx.executeWithoutResult(s -> cupos.ocupar(List.of(new LineaCupo(id, 3))));
        mvc.perform(get("/api/v1/eventos/" + id)).andExpect(jsonPath("$.cuposDisponibles").value(13));

        String version = como(vendedor, get(ADMIN + "/" + id)).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        como(admin, put(ADMIN + "/" + id).header(HttpHeaders.IF_MATCH, version).contentType(MediaType.APPLICATION_JSON)
                .content(evento("league-challenge", DiasDePrueba.a(DiasDePrueba.siguiente(), 15), null, 2, null, true)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("cuposTotales"));

        tx.executeWithoutResult(s -> cupos.liberar(List.of(new LineaCupo(id, 3))));
        mvc.perform(get("/api/v1/eventos/" + id)).andExpect(jsonPath("$.cuposDisponibles").value(16));
    }

    @Test
    void cupos_concurrencia_cuarentaHilosPorDieciseisCupos_nuncaSobreventa() throws Exception {
        long id = eventoConCupos(16);
        var largada = new CountDownLatch(1);
        var exitos = new AtomicInteger();
        var llenos = new AtomicInteger();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 40; i++) {
                pool.submit(() -> {
                    largada.await();
                    try {
                        tx.executeWithoutResult(s -> cupos.ocupar(List.of(new LineaCupo(id, 1))));
                        exitos.incrementAndGet();
                    } catch (CuposInsuficientesException e) {
                        llenos.incrementAndGet();
                    }
                    return null;
                });
            }
            largada.countDown();
        }
        assertThat(exitos.get()).isEqualTo(16);
        assertThat(llenos.get()).isEqualTo(24);
        assertThat(jdbc.queryForObject("SELECT cupos_ocupados FROM evento WHERE id = ?", Integer.class, id)).isEqualTo(16);
    }

    // ---------- seguridad ----------

    @Test
    void seguridad_mantenedor_anonimo401_cliente403_internosPasan_publicoAbierto() throws Exception {
        long id = eventoConCupos(10);
        String body = evento("otros", DiasDePrueba.a(DiasDePrueba.siguiente(), 15), null, 10, null, false);
        var endpoints = List.<java.util.function.Supplier<MockHttpServletRequestBuilder>>of(
                () -> get(ADMIN),
                () -> get(ADMIN + "/" + id),
                () -> post(ADMIN).contentType(MediaType.APPLICATION_JSON).content(body),
                () -> put(ADMIN + "/" + id).header(HttpHeaders.IF_MATCH, "999").contentType(MediaType.APPLICATION_JSON).content(body),
                () -> patch(ADMIN + "/" + id + "/publicacion").contentType(MediaType.APPLICATION_JSON).content("{\"publicado\":true}"),
                () -> delete(ADMIN + "/999999999"));
        for (int i = 0; i < endpoints.size(); i++) {
            var e = endpoints.get(i);
            mvc.perform(e.get()).andExpect(status().isUnauthorized());
            como(cliente, e.get()).andExpect(status().isForbidden());
            for (var interno : new UsuarioConToken[] {vendedor, admin}) {
                assertThat(como(interno, e.get()).andReturn().getResponse().getStatus()).as("endpoint #" + i).isNotIn(401, 403);
            }
        }
        mvc.perform(get("/api/v1/eventos")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/eventos/" + id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/agenda/ocupados").param("desde", "2027-01-01").param("hasta", "2027-01-31"))
                .andExpect(status().isOk());
    }
}
