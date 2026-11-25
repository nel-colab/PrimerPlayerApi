package cl.primerplayer.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import cl.primerplayer.TestcontainersConfiguration;
import cl.primerplayer.TokensDePrueba;
import cl.primerplayer.TokensDePrueba.UsuarioConToken;
import cl.primerplayer.shared.api.Rol;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, CorreosCapturados.Config.class})
@ActiveProfiles("test")
class CuentaClienteIntegracionTest {

    static final Duration ESPERA = Duration.ofSeconds(10);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired CorreosCapturados correos;

    // ---------- helpers ----------

    static String email() {
        return "cuenta-" + UUID.randomUUID().toString().substring(0, 8) + "@primerplayer.test";
    }

    ResultActions json(MockHttpServletRequestBuilder req, String body) throws Exception {
        return mvc.perform(req.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    ResultActions con(String token, MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    void registrar(String email, String password) throws Exception {
        json(post("/api/v1/auth/registro"), "{\"nombre\":\"Ash Ketchum\",\"email\":\"%s\",\"password\":\"%s\"}"
                .formatted(email, password)).andExpect(status().isCreated());
    }

    ResultActions login(String email, String password) throws Exception {
        return json(post("/api/v1/auth/login"), "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    ResultActions verificar(String email, String codigo) throws Exception {
        return json(post("/api/v1/auth/verificacion"), "{\"email\":\"%s\",\"codigo\":\"%s\"}".formatted(email, codigo));
    }

    ResultActions restablecer(String email, String codigo, String password) throws Exception {
        return json(post("/api/v1/auth/restablecimiento"),
                "{\"email\":\"%s\",\"codigo\":\"%s\",\"password\":\"%s\"}".formatted(email, codigo, password));
    }

    String accessToken(String email, String password) throws Exception {
        return JsonPath.read(login(email, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.accessToken");
    }

    // ---------- verificación de correo ----------

    @Test
    void registro_enviaCorreoYElCodigoVerificaUnaSolaVez() throws Exception {
        String email = email();
        registrar(email, "Pikachu2026!");
        await().atMost(ESPERA).until(() -> !correos.para(email).isEmpty());
        String codigo = correos.codigo(email, "Bienvenido");
        assertThat(correos.para(email).getFirst().texto()).contains(codigo).doesNotContain("http");

        String acceso = accessToken(email, "Pikachu2026!");
        con(acceso, get("/api/v1/me")).andExpect(jsonPath("$.emailVerified").value(false));

        verificar(email, codigo).andExpect(status().isNoContent());
        con(acceso, get("/api/v1/me"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.name").value("Ash Ketchum"))
                .andExpect(jsonPath("$.role").value("usuario"))
                .andExpect(jsonPath("$.memberSince").exists());

        verificar(email, codigo)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid-code"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM correo_enviado WHERE destino = ? AND plantilla = 'verificacion-cuenta'",
                Integer.class, email)).isEqualTo(1);
    }

    @Test
    void reenviarVerificacion_soloSiFaltaVerificar() throws Exception {
        String email = email();
        registrar(email, "Pikachu2026!");
        String acceso = accessToken(email, "Pikachu2026!");
        await().atMost(ESPERA).until(() -> correos.para(email).size() == 1);

        con(acceso, post("/api/v1/me/verificacion")).andExpect(status().isAccepted());
        await().atMost(ESPERA).until(() -> correos.para(email).size() == 2);
        // solo sirve el código del último correo
        String primero = correos.codigo(email, "Bienvenido");
        String ultimo = correos.codigo(email, "verificar el correo");
        if (!primero.equals(ultimo)) {   // (1 en un millón de que coincidan)
            verificar(email, primero)
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid-code"));
        }
        verificar(email, ultimo).andExpect(status().isNoContent());
        con(acceso, post("/api/v1/me/verificacion"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("already-verified"));
    }

    // ---------- recuperación de contraseña ----------

    @Test
    void recuperacion_noRevelaCorreosYRestableceCerrandoSesiones() throws Exception {
        String desconocido = email();
        json(post("/api/v1/auth/recuperacion"), "{\"email\":\"" + desconocido + "\"}").andExpect(status().isAccepted());

        String email = email();
        registrar(email, "Pikachu2026!");
        login(email, "Pikachu2026!").andExpect(status().isOk());
        json(post("/api/v1/auth/recuperacion"), "{\"email\":\"" + email.toUpperCase() + "\"}").andExpect(status().isAccepted());
        await().atMost(ESPERA).until(() -> correos.para(email).stream().anyMatch(c -> c.asunto().contains("restablecer")));
        String codigo = correos.codigo(email, "restablecer");

        restablecer(email, codigo, "corta").andExpect(status().isBadRequest());
        // Con espacios (como se lee en el correo) y el correo en mayúsculas también sirve
        restablecer(email.toUpperCase(), codigo.substring(0, 3) + " " + codigo.substring(3), "NuevaClave2026!")
                .andExpect(status().isNoContent());
        login(email, "Pikachu2026!").andExpect(status().isUnauthorized());
        login(email, "NuevaClave2026!").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM refresh_token r JOIN usuario u ON u.id = r.usuario_id
                WHERE u.email = ? AND r.revocado_en IS NULL""", Integer.class, email)).isEqualTo(1);   // solo la nueva
        assertThat(correos.para(desconocido)).isEmpty();

        restablecer(email, codigo, "OtraClave2026!")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid-code"));
        // Un correo no registrado responde igual (no revela cuentas)
        restablecer(desconocido, codigo, "OtraClave2026!")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid-code"));
    }

    @Test
    void codigo_alQuintoErrorVence_yTras10ErroresEnUnaHoraSeBloquea() throws Exception {
        String email = email();
        registrar(email, "Pikachu2026!");
        await().atMost(ESPERA).until(() -> !correos.para(email).isEmpty());
        String bueno = correos.codigo(email, "Bienvenido");
        String malo = bueno.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            verificar(email, malo).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid-code"));
        }
        // Al 5º error el código venció: ya no sirve ni el correcto
        verificar(email, bueno).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid-code"));

        // Pedir otro código da 5 intentos más, pero el tope de la hora (10) corta antes de poder seguir adivinando
        String acceso = accessToken(email, "Pikachu2026!");
        con(acceso, post("/api/v1/me/verificacion")).andExpect(status().isAccepted());
        await().atMost(ESPERA).until(() -> correos.para(email).size() == 2);
        for (int i = 0; i < 5; i++) {
            verificar(email, malo).andExpect(status().isBadRequest());
        }
        verificar(email, correos.codigo(email, "verificar el correo"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("too-many-attempts"));

        verificar(email, "12a456").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid-code"));
    }

    // ---------- perfil y contraseña ----------

    @Test
    void perfil_editarConRutValidoYCambiarPassword() throws Exception {
        String email = email();
        registrar(email, "Pikachu2026!");
        String acceso = accessToken(email, "Pikachu2026!");

        json(put("/api/v1/me/perfil").header(HttpHeaders.AUTHORIZATION, "Bearer " + acceso),
                "{\"nombre\":\"Ash K.\",\"telefono\":\"+56 9 1234 5678\",\"rut\":\"12.345.678-5\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ash K."))
                .andExpect(jsonPath("$.phone").value("+56 9 1234 5678"))
                .andExpect(jsonPath("$.rut").value("12345678-5"));
        json(put("/api/v1/me/perfil").header(HttpHeaders.AUTHORIZATION, "Bearer " + acceso),
                "{\"nombre\":\"Ash\",\"rut\":\"12.345.678-9\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("rut"));

        json(put("/api/v1/me/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + acceso),
                "{\"actual\":\"equivocada\",\"nueva\":\"NuevaClave2026!\"}").andExpect(status().isBadRequest());
        json(put("/api/v1/me/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + acceso),
                "{\"actual\":\"Pikachu2026!\",\"nueva\":\"NuevaClave2026!\"}").andExpect(status().isNoContent());
        login(email, "NuevaClave2026!").andExpect(status().isOk());
    }

    // ---------- direcciones, favoritos y alertas ----------

    @Test
    void direcciones_unaPrincipalMaximoCincoYSoloLasPropias() throws Exception {
        UsuarioConToken c = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
        UsuarioConToken otro = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
        String d = "{\"alias\":\"%s\",\"calle\":\"Freire 456\",\"comuna\":\"San Bernardo\",\"region\":\"RM\",\"principal\":%s}";

        long casa = ((Number) JsonPath.read(json(post("/api/v1/me/direcciones").header(HttpHeaders.AUTHORIZATION, c.bearer()),
                d.formatted("Casa", false)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.principal").value(true))   // la primera queda principal
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        json(post("/api/v1/me/direcciones").header(HttpHeaders.AUTHORIZATION, c.bearer()), d.formatted("Trabajo", true))
                .andExpect(jsonPath("$.principal").value(true));
        mvc.perform(get("/api/v1/me/direcciones").header(HttpHeaders.AUTHORIZATION, c.bearer()))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].alias").value("Trabajo"))
                .andExpect(jsonPath("$[1].principal").value(false));
        for (int i = 0; i < 3; i++) {
            json(post("/api/v1/me/direcciones").header(HttpHeaders.AUTHORIZATION, c.bearer()), d.formatted("D" + i, false))
                    .andExpect(status().isCreated());
        }
        json(post("/api/v1/me/direcciones").header(HttpHeaders.AUTHORIZATION, c.bearer()), d.formatted("Sexta", false))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/me/direcciones/" + casa).header(HttpHeaders.AUTHORIZATION, otro.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/me/direcciones/" + casa).header(HttpHeaders.AUTHORIZATION, c.bearer()))
                .andExpect(status().isNoContent());
    }

    long producto(int stock, boolean publicado) {
        return jdbc.queryForObject("""
                INSERT INTO producto (nombre, precio, categoria_id, stock, publicado)
                VALUES (?, 4990, (SELECT id FROM categoria WHERE slug = 'tcg'), ?, ?) RETURNING id
                """, Long.class, "Producto cuenta " + UUID.randomUUID().toString().substring(0, 5), stock, publicado);
    }

    @Test
    void favoritos_idempotentesYSoloProductosALaVenta() throws Exception {
        UsuarioConToken c = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
        long p = producto(3, true);
        long oculto = producto(3, false);
        mvc.perform(put("/api/v1/me/favoritos/" + p).header(HttpHeaders.AUTHORIZATION, c.bearer())).andExpect(status().isNoContent());
        mvc.perform(put("/api/v1/me/favoritos/" + p).header(HttpHeaders.AUTHORIZATION, c.bearer())).andExpect(status().isNoContent());
        mvc.perform(put("/api/v1/me/favoritos/" + oculto).header(HttpHeaders.AUTHORIZATION, c.bearer())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/me/favoritos").header(HttpHeaders.AUTHORIZATION, c.bearer()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].productId").value((int) p))
                .andExpect(jsonPath("$[0].price").value(4990));
        mvc.perform(delete("/api/v1/me/favoritos/" + p).header(HttpHeaders.AUTHORIZATION, c.bearer())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/favoritos").header(HttpHeaders.AUTHORIZATION, c.bearer())).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void alertaDeStock_alReponerseSeMarcaYLlegaElCorreo() throws Exception {
        UsuarioConToken c = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
        UsuarioConToken vendedor = TokensDePrueba.crear(jdbc, encoder, Rol.VENDEDOR);
        long agotado = producto(0, true);
        mvc.perform(put("/api/v1/me/alertas-stock/" + agotado).header(HttpHeaders.AUTHORIZATION, c.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/alertas-stock").header(HttpHeaders.AUTHORIZATION, c.bearer()))
                .andExpect(jsonPath("$[0].notifiedAt").doesNotExist());

        json(post("/api/v1/admin/inventario/" + agotado + "/ajustes").header(HttpHeaders.AUTHORIZATION, vendedor.bearer()),
                "{\"delta\":5,\"motivo\":\"Llegó reposición\"}").andExpect(status().isOk());

        await().atMost(ESPERA).until(() -> correos.para(c.email()).stream().anyMatch(m -> m.asunto().startsWith("Volvió el stock")));
        assertThat(correos.para(c.email()).getFirst().texto()).doesNotContain("http").doesNotContain("/producto/");
        mvc.perform(get("/api/v1/me/alertas-stock").header(HttpHeaders.AUTHORIZATION, c.bearer()))
                .andExpect(jsonPath("$[0].notifiedAt").exists());
    }

    @Test
    void pedidoConfirmado_enviaCorreoDeConfirmacion() throws Exception {
        UsuarioConToken c = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
        long gratis = jdbc.queryForObject("""
                INSERT INTO evento (titulo, tipo, fecha_hora, lugar, precio, cupos_totales, descripcion, publicado)
                VALUES ('Lanzamiento gratis', 'lanzamiento', now() + interval '5 days', 'Tienda', 0, 20, 'x', true) RETURNING id
                """, Long.class);
        String codigo = JsonPath.read(json(post("/api/v1/checkout").header(HttpHeaders.AUTHORIZATION, c.bearer())
                        .header("Idempotency-Key", UUID.randomUUID().toString()),
                "{\"lineas\":[{\"type\":\"event\",\"itemId\":%d,\"quantity\":1}]}".formatted(gratis))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.order.code");
        await().atMost(ESPERA).until(() -> correos.para(c.email()).stream().anyMatch(m -> m.asunto().contains(codigo)));
    }

    @Test
    void seguridad() throws Exception {
        UsuarioConToken vendedor = TokensDePrueba.crear(jdbc, encoder, Rol.VENDEDOR);
        mvc.perform(get("/api/v1/me/direcciones")).andExpect(status().isUnauthorized());
        // el personal también tiene cuenta de compra
        mvc.perform(get("/api/v1/me/direcciones").header(HttpHeaders.AUTHORIZATION, vendedor.bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/favoritos").header(HttpHeaders.AUTHORIZATION, vendedor.bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/perfil")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/perfil").header(HttpHeaders.AUTHORIZATION, vendedor.bearer())).andExpect(status().isOk());
    }
}
