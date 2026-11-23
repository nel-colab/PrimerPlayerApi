package cl.primerplayer.identidad;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import cl.primerplayer.TestcontainersConfiguration;
import cl.primerplayer.TokensDePrueba;
import cl.primerplayer.TokensDePrueba.UsuarioConToken;
import cl.primerplayer.shared.api.Rol;

/** El personal compra como cualquier cliente y necesita nombre, teléfono y RUT para trabajar en /admin/**. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PerfilPersonalIntegracionTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;

    ResultActions como(UsuarioConToken u, MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.header(HttpHeaders.AUTHORIZATION, u.bearer()));
    }

    UsuarioConToken sinPerfil(Rol rol) {
        UsuarioConToken u = TokensDePrueba.crear(jdbc, encoder, rol);
        jdbc.update("UPDATE usuario SET telefono = NULL, rut = NULL WHERE id = ?", u.id());
        return u;
    }

    @Test
    void personalSinPerfilCompleto_noUsaAdminHastaCompletarlo() throws Exception {
        for (Rol rol : new Rol[] {Rol.VENDEDOR, Rol.ADMIN}) {
            UsuarioConToken u = sinPerfil(rol);

            como(u, get("/api/v1/admin/productos")).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("profile-incomplete"))
                    .andExpect(jsonPath("$.detail").value(containsString("telefono, rut")));
            como(u, get("/api/v1/me")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.profileComplete").value(false));

            como(u, put("/api/v1/me/perfil").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nombre\":\"Ash Ketchum\",\"telefono\":\"+56 9 8765 4321\",\"rut\":\"12.345.678-5\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.profileComplete").value(true));

            // desbloqueo inmediato, sin renovar el token
            como(u, get("/api/v1/admin/productos")).andExpect(status().isOk());
        }
    }

    @Test
    void soloFaltaRut_indicaElCampo() throws Exception {
        UsuarioConToken u = TokensDePrueba.crear(jdbc, encoder, Rol.ADMIN);
        jdbc.update("UPDATE usuario SET rut = NULL WHERE id = ?", u.id());
        como(u, get("/api/v1/admin/usuarios")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("profile-incomplete"))
                .andExpect(jsonPath("$.detail").value(containsString("Falta: rut.")));
    }

    @Test
    void clienteSigueSinAcceso_aAdmin() throws Exception {
        como(sinPerfil(Rol.CLIENTE), get("/api/v1/admin/productos")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void personalUsaSuCuentaDeCompra() throws Exception {
        for (Rol rol : new Rol[] {Rol.CLIENTE, Rol.VENDEDOR, Rol.ADMIN}) {
            UsuarioConToken u = sinPerfil(rol);   // comprar no exige perfil completo
            como(u, get("/api/v1/me/pedidos")).andExpect(status().isOk());
            como(u, get("/api/v1/me/direcciones")).andExpect(status().isOk());
            como(u, get("/api/v1/me/favoritos")).andExpect(status().isOk());
            como(u, get("/api/v1/me/inscripciones")).andExpect(status().isOk());
            como(u, get("/api/v1/me/solicitudes-cumpleanos")).andExpect(status().isOk());
        }
    }
}
