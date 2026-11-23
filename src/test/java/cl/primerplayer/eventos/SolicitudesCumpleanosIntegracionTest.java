package cl.primerplayer.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import cl.primerplayer.DiasDePrueba;
import cl.primerplayer.TestcontainersConfiguration;
import cl.primerplayer.TokensDePrueba;
import cl.primerplayer.TokensDePrueba.UsuarioConToken;
import cl.primerplayer.eventos.events.SolicitudCumpleanosEventos;
import cl.primerplayer.shared.api.Rol;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@RecordApplicationEvents
class SolicitudesCumpleanosIntegracionTest {

    static final String ME = "/api/v1/me/solicitudes-cumpleanos";
    static final String TIENDA = "/api/v1/admin/solicitudes-cumpleanos";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired ApplicationEvents eventos;

    UsuarioConToken vendedor, cliente, otroCliente;

    @BeforeEach
    void usuarios() {
        vendedor = TokensDePrueba.crear(jdbc, encoder, Rol.VENDEDOR);
        cliente = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
        otroCliente = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
    }

    private ResultActions como(UsuarioConToken u, MockHttpServletRequestBuilder r) throws Exception {
        return mvc.perform(r.header(HttpHeaders.AUTHORIZATION, u.bearer()));
    }

    static String solicitud(LocalDate dia, int desde, int hasta) {
        return """
            {"inicio":"%s","fin":"%s","festejado":"Martina (8 años)","invitados":15,"telefono":"+56912345678",
             "comentario":"Torneo para los niños"}""".formatted(DiasDePrueba.a(dia, desde), DiasDePrueba.a(dia, hasta));
    }

    private long solicitar(UsuarioConToken u, LocalDate dia, int desde, int hasta) throws Exception {
        String r = como(u, post(ME).contentType(MediaType.APPLICATION_JSON).content(solicitud(dia, desde, hasta)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(r, "$.id")).longValue();
    }

    private ResultActions aprobar(long id) throws Exception {
        return como(vendedor, post(TIENDA + "/" + id + "/aprobacion"));
    }

    @Test
    void flujo_solicitarPendiente_aprobar_publicaBloqueSinDatosDelCliente() throws Exception {
        LocalDate dia = DiasDePrueba.siguiente();
        long id = solicitar(cliente, dia, 15, 18);

        como(cliente, get(ME)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value((int) id))
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$[0].clienteId").doesNotExist());
        como(vendedor, get(TIENDA)).andExpect(jsonPath("$.content[*].id").value(hasItem((int) id)));
        assertThat(eventos.stream(SolicitudCumpleanosEventos.Recibida.class).filter(e -> e.solicitudId() == id)).hasSize(1);

        String r = aprobar(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.eventoId").isNumber())
                .andReturn().getResponse().getContentAsString();
        long eventoId = ((Number) JsonPath.read(r, "$.eventoId")).longValue();

        mvc.perform(get("/api/v1/eventos/" + eventoId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("cumpleanos"))
                .andExpect(jsonPath("$.tiendaCerrada").value(true))
                .andExpect(jsonPath("$.titulo").value("Cumpleaños privado · tienda cerrada"))
                .andExpect(jsonPath("$.descripcion").value(
                        "La tienda estará cerrada al público de 15:00 a 18:00 hrs por un evento privado."))
                .andExpect(jsonPath("$.fecha").value(org.hamcrest.Matchers.startsWith(DiasDePrueba.a(dia, 15))));
        String publico = mvc.perform(get("/api/v1/eventos/" + eventoId)).andReturn().getResponse().getContentAsString();
        assertThat(publico).doesNotContain("Martina").doesNotContain("+56912345678");

        assertThat(eventos.stream(SolicitudCumpleanosEventos.Resuelta.class).filter(e -> e.solicitudId() == id))
                .singleElement().satisfies(e -> assertThat(e.aprobada()).isTrue());
        aprobar(id).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("request-not-pending"));
    }

    @Test
    void aprobacion_conTextosPersonalizados() throws Exception {
        long id = solicitar(cliente, DiasDePrueba.siguiente(), 12, 14);
        como(vendedor, post(TIENDA + "/" + id + "/aprobacion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\":\"Cumpleaños de Martina\",\"consideraciones\":[\"Tienda cerrada al público\"]}"))
                .andExpect(status().isOk());
        long eventoId = jdbc.queryForObject("SELECT evento_id FROM solicitud_cumpleanos WHERE id = ?", Long.class, id);
        mvc.perform(get("/api/v1/eventos/" + eventoId))
                .andExpect(jsonPath("$.titulo").value("Cumpleaños de Martina"))
                .andExpect(jsonPath("$.consideraciones[0]").value("Tienda cerrada al público"));
    }

    @Test
    void bloqueYaTomado_slotTakenAlSolicitarYAlAprobar() throws Exception {
        LocalDate dia = DiasDePrueba.siguiente();
        long a = solicitar(cliente, dia, 15, 18);
        long b = solicitar(otroCliente, dia, 16, 19);   // ambas pendientes: la tienda elige
        aprobar(a).andExpect(status().isOk());

        aprobar(b).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("slot-taken"));
        como(otroCliente, post(ME).contentType(MediaType.APPLICATION_JSON).content(solicitud(dia, 17, 19)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("slot-taken"));
        solicitar(otroCliente, dia, 18, 20);   // empieza justo al terminar: libre
    }

    @Test
    void concurrencia_dosVendedoresApruebanBloquesTraslapados_soloUnoGana() throws Exception {
        LocalDate dia = DiasDePrueba.siguiente();
        long a = solicitar(cliente, dia, 15, 18);
        long b = solicitar(otroCliente, dia, 17, 20);
        var largada = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (long id : new long[] {a, b}) {
                resultados.add(pool.submit(() -> {
                    largada.await();
                    return aprobar(id).andReturn().getResponse().getStatus();
                }));
            }
            largada.countDown();
        }
        List<Integer> estados = new ArrayList<>();
        for (var f : resultados) estados.add(f.get());
        assertThat(estados).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM evento WHERE tipo = 'cumpleanos' AND eliminado_en IS NULL
                  AND fecha_hora::date BETWEEN ?::date - 1 AND ?::date + 1""", Integer.class, dia.toString(), dia.toString()))
                .isEqualTo(1);
    }

    @Test
    void rechazar_exigeMotivo_yElClienteLoVe() throws Exception {
        long id = solicitar(cliente, DiasDePrueba.siguiente(), 15, 17);
        como(vendedor, post(TIENDA + "/" + id + "/rechazo").contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"\"}"))
                .andExpect(status().isBadRequest());
        como(vendedor, post(TIENDA + "/" + id + "/rechazo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Ese día hay inventario\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RECHAZADA"));
        como(cliente, get(ME)).andExpect(jsonPath("$[0].motivoRechazo").value("Ese día hay inventario"));
        como(vendedor, get(TIENDA)).andExpect(jsonPath("$.content[*].id").value(not(hasItem((int) id))));   // ya no pendiente
        como(vendedor, get(TIENDA).param("estado", "rechazada")).andExpect(jsonPath("$.content[*].id").value(hasItem((int) id)));
    }

    @Test
    void cancelar_soloLaPropiaYPendiente_ajena404() throws Exception {
        long id = solicitar(cliente, DiasDePrueba.siguiente(), 15, 17);
        como(otroCliente, post(ME + "/" + id + "/cancelacion")).andExpect(status().isNotFound());
        como(otroCliente, get(ME)).andExpect(jsonPath("$[*].id").value(not(hasItem((int) id))));
        como(cliente, post(ME + "/" + id + "/cancelacion")).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));
        como(cliente, post(ME + "/" + id + "/cancelacion")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("request-not-pending"));
    }

    @Test
    void validaciones_anticipacionYDuracion() throws Exception {
        LocalDate manana = LocalDate.now(cl.primerplayer.shared.api.FechasTienda.ZONA).plusDays(1);
        como(cliente, post(ME).contentType(MediaType.APPLICATION_JSON).content(solicitud(manana, 15, 17)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("inicio"));
        como(cliente, post(ME).contentType(MediaType.APPLICATION_JSON).content(solicitud(DiasDePrueba.siguiente(), 10, 20)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("fin"));
        como(cliente, post(ME).contentType(MediaType.APPLICATION_JSON).content("{\"invitados\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
    }

    @Test
    void seguridad_meConCualquierSesion_bandejaSoloInternos() throws Exception {
        LocalDate dia = DiasDePrueba.siguiente();
        mvc.perform(get(ME)).andExpect(status().isUnauthorized());
        // el personal también puede pedir un cumpleaños para sí
        como(vendedor, get(ME)).andExpect(status().isOk());
        como(vendedor, post(ME).contentType(MediaType.APPLICATION_JSON).content(solicitud(dia, 15, 17)))
                .andExpect(status().isCreated());
        mvc.perform(get(TIENDA)).andExpect(status().isUnauthorized());
        como(cliente, get(TIENDA)).andExpect(status().isForbidden());
        como(cliente, post(TIENDA + "/1/aprobacion")).andExpect(status().isForbidden());
        como(cliente, post(TIENDA + "/1/rechazo").contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}"))
                .andExpect(status().isForbidden());
        TokensDePrueba.UsuarioConToken admin = TokensDePrueba.crear(jdbc, encoder, Rol.ADMIN);
        como(admin, get(TIENDA)).andExpect(status().isOk());
    }
}
