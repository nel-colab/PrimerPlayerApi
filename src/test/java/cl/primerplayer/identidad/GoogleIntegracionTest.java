package cl.primerplayer.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import cl.primerplayer.TestcontainersConfiguration;
import cl.primerplayer.TokensDePrueba;
import cl.primerplayer.TokensDePrueba.UsuarioConToken;
import cl.primerplayer.identidad.api.GoogleNoDisponibleException;
import cl.primerplayer.identidad.api.GoogleTokenInvalidoException;
import cl.primerplayer.identidad.application.port.out.VerificadorGoogle;
import cl.primerplayer.identidad.application.port.out.VerificadorGoogle.IdentidadGoogle;
import cl.primerplayer.shared.api.Rol;

/** Inicio de sesión y verificación de correo con Google. El verificador del ID token se simula (no llama a Google). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class GoogleIntegracionTest {

    static final String PASSWORD = "Pikachu2026!";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @MockitoBean VerificadorGoogle verificador;

    // ---------- helpers ----------

    /** El "ID token" de prueba es una etiqueta: el verificador simulado devuelve la identidad asociada. */
    String token(String email, boolean verificado) {
        String sub = "g-" + UUID.randomUUID();
        return token(sub, email, verificado);
    }

    String token(String sub, String email, boolean verificado) {
        String etiqueta = "tok-" + UUID.randomUUID();
        when(verificador.verificar(etiqueta)).thenReturn(new IdentidadGoogle(sub, email, verificado, "Misty Waterflower"));
        return etiqueta;
    }

    static String correo() {
        return "g-" + UUID.randomUUID().toString().substring(0, 8) + "@primerplayer.test";
    }

    ResultActions loginGoogle(String credential) throws Exception {
        return mvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON)
                .content("{\"credential\":\"%s\"}".formatted(credential)));
    }

    ResultActions verificarConGoogle(String bearer, String credential) throws Exception {
        var req = post("/api/v1/me/verificacion/google").contentType(MediaType.APPLICATION_JSON)
                .content("{\"credential\":\"%s\"}".formatted(credential));
        return mvc.perform(bearer == null ? req : req.header(HttpHeaders.AUTHORIZATION, bearer));
    }

    String registrar(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Misty\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        String json = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(json, "$.accessToken");
    }

    boolean verificado(String email) {
        return jdbc.queryForObject("SELECT email_verificado_en IS NOT NULL FROM usuario WHERE email = ?", Boolean.class, email);
    }

    // ---------- inicio de sesión ----------

    @Test
    void correoNuevo_creaClienteVerificadoSinPasswordYAbreSesion() throws Exception {
        String email = correo();
        String json = loginGoogle(token(email.toUpperCase(), true))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("pp_rt=")))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("usuario"))
                .andReturn().getResponse().getContentAsString();

        var fila = jdbc.queryForMap("SELECT rol, password_hash, google_sub, email_verificado_en FROM usuario WHERE email = ?", email);
        assertThat(fila.get("rol")).isEqualTo("CLIENTE");
        assertThat(fila.get("password_hash")).isNull();
        assertThat(fila.get("google_sub")).isNotNull();
        assertThat(fila.get("email_verificado_en")).isNotNull();

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + JsonPath.read(json, "$.accessToken")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true));

        // sin contraseña: el login con contraseña no entra (mismo error genérico)
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mismaCuentaDeGoogle_vuelveAEntrarALaMismaCuenta() throws Exception {
        String email = correo();
        String sub = "g-" + UUID.randomUUID();
        loginGoogle(token(sub, email, true)).andExpect(status().isOk());
        loginGoogle(token(sub, email, true)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE email = ?", Integer.class, email)).isEqualTo(1);
    }

    @Test
    void cuentaConPasswordSinVerificar_googleLaVinculaYVerifica() throws Exception {
        String email = correo();
        registrar(email);
        assertThat(verificado(email)).isFalse();

        loginGoogle(token(email, true)).andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value(email));

        assertThat(verificado(email)).isTrue();
        assertThat(jdbc.queryForObject("SELECT google_sub FROM usuario WHERE email = ?", String.class, email)).isNotNull();
    }

    @Test
    void correoYaVinculadoAOtraCuentaDeGoogle_409() throws Exception {
        String email = correo();
        loginGoogle(token(email, true)).andExpect(status().isOk());
        loginGoogle(token(email, true))   // otro sub, mismo correo
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("google-account-in-use"));
    }

    @Test
    void googleNoConfirmaElCorreo_401() throws Exception {
        String email = correo();
        loginGoogle(token(email, false))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("invalid-google-token"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE email = ?", Integer.class, email)).isZero();
    }

    @Test
    void configPublica() throws Exception {
        when(verificador.clientId()).thenReturn(java.util.Optional.of("123.apps.googleusercontent.com"));
        mvc.perform(get("/api/v1/auth/google/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("123.apps.googleusercontent.com"))
                .andExpect(jsonPath("$.enabled").value(true));
        when(verificador.clientId()).thenReturn(java.util.Optional.empty());
        mvc.perform(get("/api/v1/auth/google/config")).andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void tokenInvalidoOGoogleCaido() throws Exception {
        when(verificador.verificar("malo")).thenThrow(new GoogleTokenInvalidoException());
        when(verificador.verificar("caido")).thenThrow(new GoogleNoDisponibleException());
        loginGoogle("malo").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("invalid-google-token"));
        loginGoogle("caido").andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("google-unavailable"));
        mvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
    }

    @Test
    void cuentaBaneada_noEntraConGoogle() throws Exception {
        String email = correo();
        registrar(email);
        jdbc.update("UPDATE usuario SET baneo_motivo = 'fraude', baneo_creado_en = now() WHERE email = ?", email);
        loginGoogle(token(email, true)).andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("account-banned"));
    }

    // ---------- verificación del correo con sesión ----------

    @Test
    void verificarConGoogle_marcaElCorreoYPermiteEntrarConGoogleDespues() throws Exception {
        String email = correo();
        String bearer = registrar(email);
        String sub = "g-" + UUID.randomUUID();

        verificarConGoogle(bearer, token(sub, email, true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true));
        assertThat(verificado(email)).isTrue();

        verificarConGoogle(bearer, token(sub, email, true)).andExpect(status().isOk());   // idempotente
        loginGoogle(token(sub, email, true)).andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value(email));
    }

    @Test
    void verificarConGoogle_correoDistinto409_ySinSesion401() throws Exception {
        String bearer = registrar(correo());
        verificarConGoogle(bearer, token(correo(), true))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("google-email-mismatch"));
        verificarConGoogle(null, "x").andExpect(status().isUnauthorized());
    }

    @Test
    void verificarConGoogle_cuentaDeGoogleDeOtroUsuario409() throws Exception {
        String sub = "g-" + UUID.randomUUID();
        loginGoogle(token(sub, correo(), true)).andExpect(status().isOk());
        String email = correo();
        String bearer = registrar(email);
        verificarConGoogle(bearer, token(sub, email, true))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("google-account-in-use"));
    }

    // ---------- el personal necesita el correo verificado ----------

    @Test
    void personalSinCorreoVerificado_noUsaElPanelHastaVerificarConGoogle() throws Exception {
        for (Rol rol : new Rol[] {Rol.VENDEDOR, Rol.ADMIN}) {
            UsuarioConToken u = TokensDePrueba.crear(jdbc, encoder, rol);
            jdbc.update("UPDATE usuario SET email_verificado_en = NULL WHERE id = ?", u.id());

            mvc.perform(get("/api/v1/admin/productos").header(HttpHeaders.AUTHORIZATION, u.bearer()))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("email-not-verified"));

            verificarConGoogle(u.bearer(), token(u.email(), true)).andExpect(status().isOk());

            mvc.perform(get("/api/v1/admin/productos").header(HttpHeaders.AUTHORIZATION, u.bearer()))
                    .andExpect(status().isOk());
        }
    }
}
