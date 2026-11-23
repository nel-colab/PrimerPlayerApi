package cl.primerplayer.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import cl.primerplayer.TestcontainersConfiguration;
import cl.primerplayer.TokensDePrueba;
import cl.primerplayer.TokensDePrueba.UsuarioConToken;
import cl.primerplayer.shared.api.Rol;

/**
 * Errores del propio Spring MVC: conservan su estado HTTP (antes caían en un 500 genérico y dejaban un "Error
 * inesperado" en el log). Un anónimo en una ruta protegida o inexistente sigue recibiendo 401 de Spring Security.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ErroresWebIntegracionTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;

    UsuarioConToken cliente;

    @BeforeEach
    void setUp() {
        cliente = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
    }

    @Test
    void anonimoEnRutaInexistente_401() throws Exception {
        mvc.perform(get("/api/v1/no-existe")).andExpect(status().isUnauthorized());
    }

    @Test
    void conSesionRutaInexistente_404NotFound() throws Exception {
        mvc.perform(get("/api/v1/no-existe").header(HttpHeaders.AUTHORIZATION, cliente.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not-found"));
    }

    @Test
    void metodoNoPermitido_405() throws Exception {
        mvc.perform(delete("/api/v1/categorias").header(HttpHeaders.AUTHORIZATION, cliente.bearer()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("method-not-allowed"));
    }

    @Test
    void idConTipoInvalido_400Validation() throws Exception {
        mvc.perform(get("/api/v1/productos/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"))
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }
}
