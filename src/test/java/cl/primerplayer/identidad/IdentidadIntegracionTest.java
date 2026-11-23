package cl.primerplayer.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import cl.primerplayer.TestcontainersConfiguration;
import cl.primerplayer.identidad.domain.TokenOpaco;
import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class IdentidadIntegracionTest {

    static final String PASSWORD = "Pikachu2026!";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private static String correoUnico() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + "@primerplayer.test";
    }

    private ResultActions registrar(String nombre, String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nombre":"%s","email":"%s","password":"%s"}""".formatted(nombre, email, password)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.USER_AGENT, "JUnit")
                .content("""
                    {"email":"%s","password":"%s"}""".formatted(email, password)));
    }

    private String tokenDe(String rol) throws Exception {
        String email = correoUnico();
        registrar("Usuario " + rol, email, PASSWORD).andExpect(status().isCreated());
        jdbc.update("UPDATE usuario SET rol = ? WHERE email = ?", rol, email);
        String body = login(email, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    // ---------- registro ----------

    @Test
    void registro_201ConFormaDelFrontendYTokenDeVerificacion() throws Exception {
        String email = correoUnico();
        registrar("Ash Ketchum", email.toUpperCase(), PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/me"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("Ash Ketchum"))
                .andExpect(jsonPath("$.role").value("usuario"));

        String hash = jdbc.queryForObject("SELECT password_hash FROM usuario WHERE email = ?", String.class, email);
        assertThat(hash).startsWith("$2").doesNotContain(PASSWORD);
        // notificaciones emite el enlace al enviar el correo (después del commit, en otro hilo)
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM token_un_uso t JOIN usuario u ON u.id = t.usuario_id
                WHERE u.email = ? AND t.tipo = 'VERIFICACION' AND t.usado_en IS NULL""", Integer.class, email))
                .isEqualTo(1));
        // el outbox guarda el evento sin token: solo usuarioId, email, nombre y solicitudId
        assertThat(jdbc.queryForList("""
                SELECT serialized_event FROM event_publication
                WHERE event_type = 'cl.primerplayer.identidad.events.UsuarioRegistrado' AND serialized_event LIKE ?""",
                String.class, "%" + email + "%"))
                .allSatisfy(json -> assertThat(json).doesNotContainIgnoringCase("token"));
    }

    @Test
    void registro_correoRepetidoConOtrasMayusculas_409EmailTaken() throws Exception {
        String email = correoUnico();
        registrar("Ash", email, PASSWORD).andExpect(status().isCreated());
        registrar("Ash", email.toUpperCase(), PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.code").value("email-taken"));
    }

    @Test
    void registro_camposInvalidos_400ValidationConCampos() throws Exception {
        registrar("", "no-es-correo", "corta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors[*].field").value(
                        org.hamcrest.Matchers.containsInAnyOrder("nombre", "email", "password")));
    }

    // ---------- login ----------

    @Test
    void login_200ConAccessTokenYCookieHttpOnly_guardaSoloHashDelRefresh() throws Exception {
        String email = correoUnico();
        registrar("Misty", email, PASSWORD).andExpect(status().isCreated());

        var res = login(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("usuario"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("pp_rt="), containsString("HttpOnly"), containsString("SameSite=Strict"),
                        containsString("Path=/api/v1/auth"))))
                .andReturn().getResponse();

        String refresh = res.getCookie("pp_rt").getValue();
        var fila = jdbc.queryForMap("""
                SELECT r.token_hash, host(r.ip) AS ip, r.user_agent FROM refresh_token r
                JOIN usuario u ON u.id = r.usuario_id WHERE u.email = ?""", email);
        assertThat(fila.get("token_hash")).isEqualTo(TokenOpaco.hashDe(refresh));
        assertThat(fila.get("ip")).isEqualTo("127.0.0.1");
        assertThat(fila.get("user_agent")).isEqualTo("JUnit");
    }

    @Test
    void login_passwordIncorrecta_401Unauthorized() throws Exception {
        String email = correoUnico();
        registrar("Misty", email, PASSWORD).andExpect(status().isCreated());
        login(email, "incorrecta")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
        login(correoUnico(), "incorrecta")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Correo o contraseña incorrectos."));
    }

    @Test
    void login_cincoFallos_423AccountLockedPersistido() throws Exception {
        String email = correoUnico();
        registrar("Brock", email, PASSWORD).andExpect(status().isCreated());
        for (int i = 0; i < 4; i++) {
            login(email, "mala" + i).andExpect(status().isUnauthorized());
        }
        login(email, "mala5").andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("account-locked"));
        login(email, PASSWORD).andExpect(status().isLocked());

        Boolean bloqueado = jdbc.queryForObject(
                "SELECT bloqueado_hasta > now() FROM usuario WHERE email = ?", Boolean.class, email);
        assertThat(bloqueado).isTrue();
    }

    @Test
    void login_clienteAnonimoDeMostrador_401() throws Exception {
        login("mostrador@primerplayer.cl", "cualquiera").andExpect(status().isUnauthorized());
    }

    // ---------- refresh ----------

    private ResultActions refresh(String cookie, boolean conHeader) throws Exception {
        var req = post("/api/v1/auth/refresh");
        if (cookie != null) req.cookie(new Cookie("pp_rt", cookie));
        if (conHeader) req.header("X-Requested-With", "pp-web");
        return mvc.perform(req);
    }

    @Test
    void refresh_rotaLaCookieYEntregaUnAccessTokenValido() throws Exception {
        String email = correoUnico();
        registrar("Misty", email, PASSWORD).andExpect(status().isCreated());
        String inicial = refreshDeNuevaSesion(email);

        var res = refresh(inicial, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(containsString("pp_rt="),
                        containsString("HttpOnly"), containsString("Path=/api/v1/auth"))))
                .andReturn().getResponse();
        String nuevo = res.getCookie("pp_rt").getValue();
        assertThat(nuevo).isNotEqualTo(inicial);

        String access = JsonPath.read(res.getContentAsString(), "$.accessToken");
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));

        var viejo = jdbc.queryForMap("SELECT revocado_en, reemplazado_por, familia FROM refresh_token WHERE token_hash = ?",
                TokenOpaco.hashDe(inicial));
        var sucesor = jdbc.queryForMap("SELECT id, familia, revocado_en FROM refresh_token WHERE token_hash = ?",
                TokenOpaco.hashDe(nuevo));
        assertThat(viejo.get("revocado_en")).isNotNull();
        assertThat(viejo.get("reemplazado_por")).isEqualTo(sucesor.get("id"));
        assertThat(sucesor.get("familia")).isEqualTo(viejo.get("familia"));
        assertThat(sucesor.get("revocado_en")).isNull();

        refresh(nuevo, true).andExpect(status().isOk());   // se puede encadenar
    }

    @Test
    void refresh_sinHeaderPpWeb_403_sinCookie_401() throws Exception {
        String email = correoUnico();
        registrar("Brock", email, PASSWORD).andExpect(status().isCreated());
        String token = refreshDeNuevaSesion(email);

        refresh(token, false).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
        assertThat(vigentes(email)).isEqualTo(1);   // no tocó el token
        refresh(null, true).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("unauthorized"));
        refresh("no-existe", true).andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_despuesDelLogout_401() throws Exception {
        String email = correoUnico();
        registrar("Gary", email, PASSWORD).andExpect(status().isCreated());
        String token = refreshDeNuevaSesion(email);
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("pp_rt", token))).andExpect(status().isNoContent());
        refresh(token, true).andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_reusoDeUnTokenRotado_revocaLaFamiliaCompleta() throws Exception {
        String email = correoUnico();
        registrar("Erika", email, PASSWORD).andExpect(status().isCreated());
        String robado = refreshDeNuevaSesion(email);
        String legitimo = refresh(robado, true).andExpect(status().isOk()).andReturn().getResponse()
                .getCookie("pp_rt").getValue();
        // Simula que el reúso ocurre después de la ventana de gracia
        jdbc.update("UPDATE refresh_token SET revocado_en = now() - interval '1 minute' WHERE token_hash = ?",
                TokenOpaco.hashDe(robado));

        refresh(robado, true).andExpect(status().isUnauthorized());
        assertThat(vigentes(email)).isZero();
        refresh(legitimo, true).andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_dentroDeLaGracia_401PeroLaSesionSigue() throws Exception {
        String email = correoUnico();
        registrar("Sabrina", email, PASSWORD).andExpect(status().isCreated());
        String inicial = refreshDeNuevaSesion(email);
        String nuevo = refresh(inicial, true).andReturn().getResponse().getCookie("pp_rt").getValue();

        refresh(inicial, true).andExpect(status().isUnauthorized());   // otra pestaña con la cookie vieja
        assertThat(vigentes(email)).isEqualTo(1);
        refresh(nuevo, true).andExpect(status().isOk());              // reintento con la cookie nueva
    }

    @Test
    void refresh_usuarioDesactivado_401() throws Exception {
        String email = correoUnico();
        registrar("Koga", email, PASSWORD).andExpect(status().isCreated());
        String token = refreshDeNuevaSesion(email);
        jdbc.update("UPDATE usuario SET activo = FALSE WHERE email = ?", email);
        refresh(token, true).andExpect(status().isUnauthorized());
        assertThat(vigentes(email)).isZero();
    }

    // ---------- logout ----------

    private String refreshDeNuevaSesion(String email) throws Exception {
        return login(email, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getCookie("pp_rt").getValue();
    }

    private Integer vigentes(String email) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM refresh_token r JOIN usuario u ON u.id = r.usuario_id
                WHERE u.email = ? AND r.revocado_en IS NULL""", Integer.class, email);
    }

    @Test
    void logout_204RevocaSoloEsaSesionYBorraLaCookie() throws Exception {
        String email = correoUnico();
        registrar("Misty", email, PASSWORD).andExpect(status().isCreated());
        String celular = refreshDeNuevaSesion(email);
        refreshDeNuevaSesion(email);   // otra sesión (otro dispositivo) del mismo usuario
        assertThat(vigentes(email)).isEqualTo(2);

        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("pp_rt", celular)))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("pp_rt="), containsString("Max-Age=0"), containsString("Path=/api/v1/auth"),
                        containsString("HttpOnly"))));

        assertThat(vigentes(email)).isEqualTo(1);
        String hashRevocado = jdbc.queryForObject("SELECT token_hash FROM refresh_token WHERE revocado_en IS NOT NULL "
                + "AND token_hash = ?", String.class, TokenOpaco.hashDe(celular));
        assertThat(hashRevocado).isNotNull();
    }

    @Test
    void logout_esIdempotente_sinCookieTokenDesconocidoORepetido() throws Exception {
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("pp_rt", "no-existe")))
                .andExpect(status().isNoContent());

        String email = correoUnico();
        registrar("Brock", email, PASSWORD).andExpect(status().isCreated());
        String refresh = refreshDeNuevaSesion(email);
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("pp_rt", refresh))).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("pp_rt", refresh))).andExpect(status().isNoContent());
        assertThat(vigentes(email)).isZero();
    }

    @Test
    void logout_publicoParaCualquierRol() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe("VENDEDOR")))
                .andExpect(status().isNoContent());
    }

    // ---------- matriz rol × endpoint ----------

    @Test
    void me_anonimo_401() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void me_tokenInvalido_401() throws Exception {
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer no.es.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_cliente_200() throws Exception {
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe("CLIENTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("usuario"));
    }

    @Test
    void me_vendedor_200() throws Exception {
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe("VENDEDOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("vendedor"));
    }

    @Test
    void me_admin_200() throws Exception {
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDe("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("admin"));
    }

    @Test
    void registroYLogin_sonPublicosParaCualquierRol() throws Exception {
        String token = tokenDe("ADMIN");
        mvc.perform(post("/api/v1/auth/login").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"x@y.cl\",\"password\":\"zzzzzzzz\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));   // credenciales, no el filtro
    }

    @Test
    void openApi_publicoEIncluyeLosEndpoints() throws Exception {
        mvc.perform(get("/api/v1/openapi.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/registro']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/me']").exists());
    }
}
