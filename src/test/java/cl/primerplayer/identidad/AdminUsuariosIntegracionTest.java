package cl.primerplayer.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

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

import com.jayway.jsonpath.JsonPath;

import cl.primerplayer.TestcontainersConfiguration;
import cl.primerplayer.TokensDePrueba;
import cl.primerplayer.TokensDePrueba.UsuarioConToken;
import cl.primerplayer.notificaciones.CorreosCapturados;
import cl.primerplayer.shared.api.Rol;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, CorreosCapturados.Config.class})
@ActiveProfiles("test")
class AdminUsuariosIntegracionTest {

    static final Duration ESPERA = Duration.ofSeconds(10);
    static final String PASSWORD = "Pikachu2026!";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired CorreosCapturados correos;

    UsuarioConToken admin;

    @BeforeEach
    void admin() {
        admin = TokensDePrueba.crear(jdbc, encoder, Rol.ADMIN);
    }

    // ---------- helpers ----------

    ResultActions como(UsuarioConToken u, MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(u == null ? req : req.header(HttpHeaders.AUTHORIZATION, u.bearer()));
    }

    ResultActions como(UsuarioConToken u, MockHttpServletRequestBuilder req, String json) throws Exception {
        return como(u, req.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Cliente real con contraseña (para probar login/refresh). */
    String registrar() throws Exception {
        String email = "adm-" + UUID.randomUUID().toString().substring(0, 8) + "@primerplayer.test";
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Misty Waterflower\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        return email;
    }

    ResultActions login(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)));
    }

    long idDe(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", Long.class, email);
    }

    void auditado(long usuarioId, String accion) {
        await().atMost(ESPERA).until(() -> jdbc.queryForObject("""
                SELECT count(*) FROM auditoria WHERE entidad = 'usuario' AND entidad_id = ? AND accion = ? AND usuario_id = ?""",
                Integer.class, String.valueOf(usuarioId), accion, admin.id()) == 1);
    }

    // ---------- seguridad ----------

    @Test
    void seguridad_soloAdmin() throws Exception {
        UsuarioConToken vendedor = TokensDePrueba.crear(jdbc, encoder, Rol.VENDEDOR);
        UsuarioConToken cliente = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
        como(null, get("/api/v1/admin/usuarios")).andExpect(status().isUnauthorized());
        como(cliente, get("/api/v1/admin/usuarios")).andExpect(status().isForbidden());
        como(vendedor, get("/api/v1/admin/usuarios")).andExpect(status().isForbidden());
        como(vendedor, get("/api/v1/admin/usuarios/indicadores")).andExpect(status().isForbidden());
        como(vendedor, post("/api/v1/admin/usuarios/" + cliente.id() + "/baneo"), "{\"motivo\":\"x\"}")
                .andExpect(status().isForbidden());
        como(vendedor, patch("/api/v1/admin/usuarios/" + cliente.id() + "/rol"), "{\"rol\":\"admin\"}")
                .andExpect(status().isForbidden());
        como(admin, get("/api/v1/admin/usuarios")).andExpect(status().isOk());
        como(admin, get("/api/v1/admin/usuarios/indicadores")).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber()).andExpect(jsonPath("$.sinVerificar").isNumber());
    }

    // ---------- búsqueda ----------

    @Test
    void buscar_porCorreoRolYEstado_conFormaDelFront() throws Exception {
        UsuarioConToken vendedor = TokensDePrueba.crear(jdbc, encoder, Rol.VENDEDOR);
        como(admin, get("/api/v1/admin/usuarios").param("q", vendedor.email()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(vendedor.id()))
                .andExpect(jsonPath("$.content[0].rol").value("vendedor"))
                .andExpect(jsonPath("$.content[0].emailVerificado").value(true))
                .andExpect(jsonPath("$.content[0].baneo").value(nullValue()))
                .andExpect(jsonPath("$.content[0].creadoEn").isString());
        como(admin, get("/api/v1/admin/usuarios").param("q", vendedor.email()).param("rol", "usuario"))
                .andExpect(jsonPath("$.totalElements").value(0));
        como(admin, get("/api/v1/admin/usuarios").param("q", String.valueOf(vendedor.id())))
                .andExpect(jsonPath("$.content[*].id", hasItem((int) vendedor.id())));

        como(admin, post("/api/v1/admin/usuarios/" + vendedor.id() + "/baneo"), "{\"motivo\":\"Prueba\"}").andExpect(status().isOk());
        como(admin, get("/api/v1/admin/usuarios").param("q", vendedor.email()).param("estado", "baneado"))
                .andExpect(jsonPath("$.totalElements").value(1));
        como(admin, get("/api/v1/admin/usuarios").param("q", vendedor.email()).param("estado", "activo"))
                .andExpect(jsonPath("$.totalElements").value(0));
        como(admin, get("/api/v1/admin/usuarios").param("estado", "otro"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
        como(admin, get("/api/v1/admin/usuarios").param("size", "500"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
    }

    // ---------- baneo ----------

    @Test
    void banear_rechazaLoginYRefreshYCierraSesiones_quitarBaneoLoDevuelve() throws Exception {
        String email = registrar();
        long id = idDe(email);
        login(email).andExpect(status().isOk());

        como(admin, post("/api/v1/admin/usuarios/" + id + "/baneo"),
                "{\"motivo\":\"Insultos en torneo\",\"hasta\":\"2099-01-01T00:00:00-03:00\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baneo.motivo").value("Insultos en torneo"))
                .andExpect(jsonPath("$.baneo.hasta").isString());

        Integer vivas = jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE usuario_id = ? AND revocado_en IS NULL",
                Integer.class, id);
        assertThat(vivas).isZero();
        login(email).andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("account-banned"));
        auditado(id, "USUARIO_BANEADO");

        como(admin, delete("/api/v1/admin/usuarios/" + id + "/baneo"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.baneo").value(nullValue()));
        login(email).andExpect(status().isOk());
        auditado(id, "BANEO_QUITADO");
    }

    @Test
    void banear_vencido_noCuenta_yValidaciones() throws Exception {
        UsuarioConToken cliente = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
        String url = "/api/v1/admin/usuarios/" + cliente.id() + "/baneo";
        como(admin, post(url), "{\"motivo\":\"\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"));
        como(admin, post(url), "{\"motivo\":\"" + "x".repeat(301) + "\"}").andExpect(status().isBadRequest());
        como(admin, post(url), "{\"motivo\":\"Pasado\",\"hasta\":\"2020-01-01T00:00:00Z\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
        como(admin, post("/api/v1/admin/usuarios/" + admin.id() + "/baneo"), "{\"motivo\":\"Yo\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("self-action"));
        como(admin, post("/api/v1/admin/usuarios/999999999/baneo"), "{\"motivo\":\"Nadie\"}")
                .andExpect(status().isNotFound());

        // un baneo ya vencido se muestra como sin baneo
        jdbc.update("UPDATE usuario SET baneo_motivo = 'viejo', baneo_hasta = now() - interval '1 day', baneo_creado_en = now() - interval '2 day' WHERE id = ?",
                cliente.id());
        como(admin, get("/api/v1/admin/usuarios").param("q", cliente.email()))
                .andExpect(jsonPath("$.content[0].baneo").value(nullValue()));
    }

    // ---------- lista negra ----------

    @Test
    void listaNegra_puedeEntrarPeroNoComprar() throws Exception {
        UsuarioConToken cliente = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
        como(admin, post("/api/v1/admin/usuarios/" + cliente.id() + "/lista-negra"), "{\"motivo\":\"Contracargos\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listaNegra.motivo").value("Contracargos"))
                .andExpect(jsonPath("$.listaNegra.creadoEn").isString());

        como(cliente, get("/api/v1/me")).andExpect(status().isOk());
        como(cliente, post("/api/v1/checkout").header("Idempotency-Key", UUID.randomUUID().toString()),
                "{\"lineas\":[{\"type\":\"product\",\"itemId\":999999999,\"quantity\":1}]}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("blacklisted"));
        auditado(cliente.id(), "LISTA_NEGRA_AGREGADO");

        como(admin, get("/api/v1/admin/usuarios").param("q", cliente.email()).param("estado", "lista-negra"))
                .andExpect(jsonPath("$.totalElements").value(1));
        como(admin, delete("/api/v1/admin/usuarios/" + cliente.id() + "/lista-negra"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.listaNegra").value(nullValue()));
        como(cliente, post("/api/v1/checkout").header("Idempotency-Key", UUID.randomUUID().toString()),
                "{\"lineas\":[{\"type\":\"product\",\"itemId\":999999999,\"quantity\":1}]}")
                .andExpect(jsonPath("$.code", not("blacklisted")));
    }

    // ---------- roles ----------

    @Test
    void cambiarRol_auditaYNoSobreSiMismo() throws Exception {
        UsuarioConToken cliente = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
        como(admin, patch("/api/v1/admin/usuarios/" + cliente.id() + "/rol"), "{\"rol\":\"vendedor\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("vendedor"));
        assertThat(jdbc.queryForObject("SELECT rol FROM usuario WHERE id = ?", String.class, cliente.id())).isEqualTo("VENDEDOR");
        auditado(cliente.id(), "ROL_CAMBIADO");
        String despues = jdbc.queryForObject("""
                SELECT despues::text FROM auditoria WHERE entidad = 'usuario' AND entidad_id = ? AND accion = 'ROL_CAMBIADO'""",
                String.class, String.valueOf(cliente.id()));
        assertThat(despues).contains("VENDEDOR");

        como(admin, patch("/api/v1/admin/usuarios/" + admin.id() + "/rol"), "{\"rol\":\"usuario\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("self-action"));
        como(admin, patch("/api/v1/admin/usuarios/" + cliente.id() + "/rol"), "{\"rol\":\"jefe\"}")
                .andExpect(status().isBadRequest());

        como(admin, get("/api/v1/admin/auditoria").param("entidad", "usuario"))
                .andExpect(status().isOk());
    }

    @Test
    void cambiarRol_otroAdmin_permitidoMientrasQuedeUno() throws Exception {
        UsuarioConToken otro = TokensDePrueba.crear(jdbc, encoder, Rol.ADMIN);
        como(admin, patch("/api/v1/admin/usuarios/" + otro.id() + "/rol"), "{\"rol\":\"usuario\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("usuario"));
    }

    // ---------- correos y sesiones ----------

    @Test
    void restablecimientoPassword_marcaPendienteYEnviaCorreo() throws Exception {
        String email = registrar();
        long id = idDe(email);
        como(admin, post("/api/v1/admin/usuarios/" + id + "/restablecimiento-password"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cambioPasswordPendiente").value(true));
        await().atMost(ESPERA).until(() -> correos.para(email).stream().anyMatch(c -> c.asunto().contains("Restablece")));
        assertThat(correos.token(email, "Restablece")).isNotBlank();
        auditado(id, "CAMBIO_PASSWORD_SOLICITADO");

        String access = JsonPath.read(login(email).andReturn().getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void reenviarVerificacion_sinVerificarEnvia_verificado409() throws Exception {
        String email = registrar();
        long id = idDe(email);
        como(admin, post("/api/v1/admin/usuarios/" + id + "/verificacion")).andExpect(status().isOk());
        await().atMost(ESPERA).until(() -> correos.para(email).size() >= 2);   // bienvenida + reenvío
        auditado(id, "VERIFICACION_REENVIADA");

        UsuarioConToken verificado = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
        como(admin, post("/api/v1/admin/usuarios/" + verificado.id() + "/verificacion"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("already-verified"));
    }

    @Test
    void cerrarSesiones_revocaRefreshTokens() throws Exception {
        String email = registrar();
        long id = idDe(email);
        login(email).andExpect(status().isOk());
        login(email).andExpect(status().isOk());
        como(admin, post("/api/v1/admin/usuarios/" + id + "/cierre-sesiones")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE usuario_id = ? AND revocado_en IS NULL",
                Integer.class, id)).isZero();
        auditado(id, "SESIONES_CERRADAS");
        como(admin, post("/api/v1/admin/usuarios/" + admin.id() + "/cierre-sesiones"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("self-action"));
    }
}
