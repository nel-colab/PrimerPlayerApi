package cl.primerplayer.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import cl.primerplayer.shared.api.Rol;

/** Aviso por correo a los ADMIN: producto nuevo o cambio de precio de un vendedor esperando aprobación. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, CorreosCapturados.Config.class})
@ActiveProfiles("test")
class AvisosAprobacionIntegracionTest {

    static final Duration ESPERA = Duration.ofSeconds(10);
    static final String BASE = "/api/v1/admin/productos";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired CorreosCapturados correos;

    UsuarioConToken admin, vendedor;

    @BeforeEach
    void usuarios() {
        admin = TokensDePrueba.crear(jdbc, encoder, Rol.ADMIN);
        vendedor = TokensDePrueba.crear(jdbc, encoder, Rol.VENDEDOR);
    }

    static String json(String nombre, long precio, String descripcion, boolean publicado) {
        return """
            {"nombre":"%s","precio":%d,"categoria":"tcg-pokemon","descripcion":"%s","publicado":%s}
            """.formatted(nombre, precio, descripcion, publicado);
    }

    ResultActions como(UsuarioConToken u, MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.header(HttpHeaders.AUTHORIZATION, u.bearer()));
    }

    long crear(UsuarioConToken u, String body) throws Exception {
        String r = como(u, post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(r, "$.id")).longValue();
    }

    ResultActions putComo(UsuarioConToken u, long id, String body) throws Exception {
        return como(u, put(BASE + "/" + id).header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    long avisos(String nombre) {
        return correos.para(admin.email()).stream().filter(c -> c.asunto().contains(nombre)).count();
    }

    static String nombre() {
        return "Aviso " + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void productoNuevoDeVendedor_avisaAlAdminConPrecioYEnlace() throws Exception {
        String n = nombre();
        crear(vendedor, json(n, 12990, "x", true));

        await().atMost(ESPERA).until(() -> avisos(n) == 1);
        var correo = correos.para(admin.email()).stream().filter(c -> c.asunto().contains(n)).findFirst().orElseThrow();
        assertThat(correo.asunto()).startsWith("Producto nuevo por aprobar: " + n);
        assertThat(correo.texto())
                .contains("Prueba VENDEDOR")
                .contains("$12.990")
                .contains("/admin/aprobaciones");
    }

    @Test
    void productoNuevoDeAdmin_noAvisa() throws Exception {
        String n = nombre();
        crear(admin, json(n, 4990, "x", true));
        Thread.sleep(1500);   // el envío es asíncrono: se da tiempo a que un aviso (incorrecto) alcance a salir
        assertThat(avisos(n)).isZero();
    }

    @Test
    void cambioDePrecio_avisaUnaVezPorPrecio_yOtrosCambiosNoAvisan() throws Exception {
        String n = nombre();
        long id = crear(admin, json(n, 4990, "x", true));

        // Solo la descripción: queda en revisión, pero sin correo
        putComo(vendedor, id, json(n, 4990, "otra descripción", true)).andExpect(status().isAccepted());
        // Precio: aviso con el precio actual y el propuesto
        putComo(vendedor, id, json(n, 5990, "otra descripción", true)).andExpect(status().isAccepted());
        await().atMost(ESPERA).until(() -> avisos(n) == 1);
        var correo = correos.para(admin.email()).stream().filter(c -> c.asunto().contains(n)).findFirst().orElseThrow();
        assertThat(correo.asunto()).startsWith("Cambio de precio por aprobar: " + n);
        assertThat(correo.texto()).contains("Precio actual: $4.990").contains("Precio propuesto: $5.990");

        // Reenviar el mismo precio no repite el aviso; un precio distinto sí avisa de nuevo
        putComo(vendedor, id, json(n, 5990, "tercera", true)).andExpect(status().isAccepted());
        putComo(vendedor, id, json(n, 6490, "tercera", true)).andExpect(status().isAccepted());
        await().atMost(ESPERA).until(() -> avisos(n) == 2);
        Thread.sleep(1000);
        assertThat(avisos(n)).isEqualTo(2);
    }

    @Test
    void republicarSinCambiarPrecio_noAvisa() throws Exception {
        String n = nombre();
        long id = crear(admin, json(n, 4990, "x", true));
        como(vendedor, patch(BASE + "/" + id + "/publicacion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"publicado\":false}")).andExpect(status().isOk());
        como(vendedor, patch(BASE + "/" + id + "/publicacion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"publicado\":true}")).andExpect(status().isAccepted());
        Thread.sleep(1500);
        assertThat(avisos(n)).isZero();
    }

    @Test
    void adminSinCorreoVerificado_noRecibeAvisos() throws Exception {
        UsuarioConToken sinVerificar = TokensDePrueba.crear(jdbc, encoder, Rol.ADMIN);
        jdbc.update("UPDATE usuario SET email_verificado_en = NULL WHERE id = ?", sinVerificar.id());
        String n = nombre();
        crear(vendedor, json(n, 4990, "x", false));

        await().atMost(ESPERA).until(() -> avisos(n) == 1);   // el admin verificado sí lo recibe
        assertThat(correos.para(sinVerificar.email())).isEmpty();
    }
}
