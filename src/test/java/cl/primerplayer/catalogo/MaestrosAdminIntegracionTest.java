package cl.primerplayer.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import cl.primerplayer.shared.api.Rol;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class MaestrosAdminIntegracionTest {

    static final String CATEGORIAS = "/api/v1/admin/categorias";
    static final String FRANQUICIAS = "/api/v1/admin/franquicias";
    static final Duration ESPERA = Duration.ofSeconds(10);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;

    UsuarioConToken admin, vendedor, cliente;

    @BeforeEach
    void usuarios() {
        admin = TokensDePrueba.crear(jdbc, encoder, Rol.ADMIN);
        vendedor = TokensDePrueba.crear(jdbc, encoder, Rol.VENDEDOR);
        cliente = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
    }

    ResultActions como(UsuarioConToken u, MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.header(HttpHeaders.AUTHORIZATION, u.bearer()).contentType(MediaType.APPLICATION_JSON));
    }

    static String unico() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    int crearCategoria(String titulo) throws Exception {
        String r = como(admin, post(CATEGORIAS).content("{\"titulo\":\"" + titulo + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(r, "$.id");
    }

    int crearFranquicia(String nombre) throws Exception {
        String r = como(admin, post(FRANQUICIAS).content("{\"nombre\":\"" + nombre + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(r, "$.id");
    }

    long producto(Integer categoriaId, Integer franquiciaId) {
        return jdbc.queryForObject("""
                INSERT INTO producto (nombre, precio, categoria_id, franquicia_id)
                VALUES ('Producto ' || ?, 1000, COALESCE(?, (SELECT id FROM categoria WHERE slug = 'tcg')), ?)
                RETURNING id""", Long.class, unico(), categoriaId, franquiciaId);
    }

    void revision(String json, String estado) {
        long productoId = producto(null, null);
        jdbc.update("""
                INSERT INTO producto_revision (producto_id, tipo, estado, datos_propuestos, version_base, solicitado_por,
                                               revisado_en, motivo_rechazo)
                VALUES (?, 'EDICION', ?, CAST(? AS jsonb), 0, ?, CASE WHEN ? = 'PENDIENTE' THEN NULL ELSE now() END,
                        CASE WHEN ? = 'RECHAZADA' THEN 'no' END)""",
                productoId, estado, json, vendedor.id(), estado, estado);
    }

    void auditado(String entidad, int id, String accion) {
        await().atMost(ESPERA).until(() -> jdbc.queryForObject("""
                SELECT count(*) FROM auditoria WHERE entidad = ? AND entidad_id = ? AND accion = ? AND usuario_id = ?""",
                Integer.class, entidad, String.valueOf(id), accion, admin.id()) == 1);
    }

    // ---------- categorías ----------

    @Test
    void crearCategoriaGeneraSlugYQuedaVisibleEnElCatalogoPublico() throws Exception {
        String u = unico();
        String r = como(admin, post(CATEGORIAS).content("{\"titulo\":\"  Juegos   de Mesa Ñandú " + u + " \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("juegos-de-mesa-nandu-" + u))
                .andExpect(jsonPath("$.titulo").value("Juegos de Mesa Ñandú " + u))
                .andExpect(jsonPath("$.productos").value(0))
                .andExpect(jsonPath("$.enUso").value(false))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(r, "$.id");

        mvc.perform(get("/api/v1/categorias")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug", hasItem("juegos-de-mesa-nandu-" + u)));
        assertThat(jdbc.queryForObject("SELECT es_hoja FROM categoria WHERE id = ?", Boolean.class, id)).isTrue();
        auditado("categoria", id, "CATEGORIA_CREADA");

        // Un producto nuevo ya la puede usar
        como(vendedor, post("/api/v1/admin/productos").content("""
                {"nombre":"Catan %s","precio":39990,"categoria":"juegos-de-mesa-nandu-%s"}""".formatted(u, u)))
                .andExpect(status().isCreated());
    }

    @Test
    void crearCategoriaConSlugYOrdenExplicitos() throws Exception {
        String u = unico();
        como(admin, post(CATEGORIAS).content("{\"titulo\":\"Cómics " + u + "\",\"slug\":\"Comics-" + u + "\",\"orden\":55}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("comics-" + u))
                .andExpect(jsonPath("$.orden").value(55));
    }

    @Test
    void categoriaDuplicadaPorSlugOTitulo() throws Exception {
        String u = unico();
        crearCategoria("Peluches " + u);

        como(admin, post(CATEGORIAS).content("{\"titulo\":\"PELUCHES " + u + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("category-exists"));
        como(admin, post(CATEGORIAS).content("{\"titulo\":\"Otro " + u + "\",\"slug\":\"peluches-" + u + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("category-exists"));
        como(admin, post(CATEGORIAS).content("{\"titulo\":\"TCG\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("category-exists"));
    }

    @Test
    void categoriaInvalida() throws Exception {
        como(admin, post(CATEGORIAS).content("{\"titulo\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
        como(admin, post(CATEGORIAS).content("{\"titulo\":\"Todos\",\"slug\":\"todos\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("slug"));
        como(admin, post(CATEGORIAS).content("{\"titulo\":\"X\",\"slug\":\"con espacios\"}"))
                .andExpect(status().isBadRequest());
        como(admin, post(CATEGORIAS).content("{\"titulo\":\"X\",\"orden\":10000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editarCategoriaCambiaTituloYOrdenPeroNoElSlug() throws Exception {
        String u = unico();
        int id = crearCategoria("Mangas " + u);

        como(admin, put(CATEGORIAS + "/" + id).content("{\"titulo\":\"Manga y Cómics " + u + "\",\"orden\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("mangas-" + u))
                .andExpect(jsonPath("$.titulo").value("Manga y Cómics " + u))
                .andExpect(jsonPath("$.orden").value(7));
        como(admin, put(CATEGORIAS + "/" + id).content("{\"titulo\":\"TCG\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("category-exists"));
        auditado("categoria", id, "CATEGORIA_EDITADA");
    }

    @Test
    void eliminarCategoriaSinUso() throws Exception {
        int id = crearCategoria("Temporal " + unico());

        como(admin, delete(CATEGORIAS + "/" + id)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM categoria WHERE id = ?", Integer.class, id)).isZero();
        como(admin, delete(CATEGORIAS + "/" + id))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("item-unavailable"));
        auditado("categoria", id, "CATEGORIA_ELIMINADA");
    }

    @Test
    void categoriaConProductoAunqueEsteEliminadoNoSeElimina() throws Exception {
        int id = crearCategoria("Usada " + unico());
        long productoId = producto(id, null);
        jdbc.update("UPDATE producto SET eliminado_en = now() WHERE id = ?", productoId);

        como(admin, get(CATEGORIAS)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].enUso").value(hasItem(true)))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].productos").value(hasItem(1)));
        como(admin, delete(CATEGORIAS + "/" + id))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("category-in-use"));
    }

    @Test
    void categoriaMencionadaSoloEnUnaRevisionNoSeElimina() throws Exception {
        String u = unico();
        int id = crearCategoria("Propuesta " + u);
        revision("{\"categoria\":\"propuesta-" + u + "\"}", "RECHAZADA");

        como(admin, delete(CATEGORIAS + "/" + id))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("category-in-use"));
    }

    @Test
    void laAgrupadoraTodosNoSeListaNiSeToca() throws Exception {
        int todos = jdbc.queryForObject("SELECT id FROM categoria WHERE slug = 'todos'", Integer.class);

        como(admin, get(CATEGORIAS)).andExpect(jsonPath("$[*].slug", not(hasItem("todos"))));
        como(admin, delete(CATEGORIAS + "/" + todos)).andExpect(status().isNotFound());
        como(admin, put(CATEGORIAS + "/" + todos).content("{\"titulo\":\"Todo\"}")).andExpect(status().isNotFound());
    }

    // ---------- franquicias ----------

    @Test
    void crudDeFranquicia() throws Exception {
        String u = unico();
        int id = crearFranquicia("  Digimon   " + u + " ");

        como(admin, get(FRANQUICIAS)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].nombre").value(hasItem("Digimon " + u)));
        mvc.perform(get("/api/v1/franquicias")).andExpect(jsonPath("$", hasItem("Digimon " + u)));

        como(admin, put(FRANQUICIAS + "/" + id).content("{\"nombre\":\"Digimon TCG " + u + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Digimon TCG " + u));
        como(admin, delete(FRANQUICIAS + "/" + id)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM franquicia WHERE id = ?", Integer.class, id)).isZero();
        auditado("franquicia", id, "FRANQUICIA_CREADA");
        auditado("franquicia", id, "FRANQUICIA_EDITADA");
        auditado("franquicia", id, "FRANQUICIA_ELIMINADA");
    }

    @Test
    void franquiciaDuplicadaSinDistinguirMayusculas() throws Exception {
        String u = unico();
        int id = crearFranquicia("Gundam " + u);
        int otra = crearFranquicia("Zoids " + u);

        como(admin, post(FRANQUICIAS).content("{\"nombre\":\"GUNDAM " + u + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("franchise-exists"));
        como(admin, put(FRANQUICIAS + "/" + otra).content("{\"nombre\":\"gundam " + u + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("franchise-exists"));
        como(admin, put(FRANQUICIAS + "/" + id).content("{\"nombre\":\"Gundam " + u + "\"}"))
                .andExpect(status().isOk());
        como(admin, post(FRANQUICIAS).content("{\"nombre\":\"\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void franquiciaConProductoNoSeElimina() throws Exception {
        int id = crearFranquicia("Sailor Moon " + unico());
        producto(null, id);

        como(admin, get(FRANQUICIAS))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].enUso").value(hasItem(true)));
        como(admin, delete(FRANQUICIAS + "/" + id))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("franchise-in-use"));
    }

    @Test
    void renombrarActualizaRevisionesPendientesYLasMantieneEnUso() throws Exception {
        String u = unico();
        int id = crearFranquicia("Chainsaw " + u);
        revision("{\"categoria\":\"figuras\",\"franquicia\":\"chainsaw " + u + "\"}", "PENDIENTE");

        como(admin, put(FRANQUICIAS + "/" + id).content("{\"nombre\":\"Chainsaw Man " + u + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enUso").value(true));

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM producto_revision WHERE datos_propuestos ->> 'franquicia' = ?""",
                Integer.class, "Chainsaw Man " + u)).isEqualTo(1);
        como(admin, delete(FRANQUICIAS + "/" + id))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("franchise-in-use"));
    }

    @Test
    void franquiciaInexistente() throws Exception {
        como(admin, put(FRANQUICIAS + "/32000").content("{\"nombre\":\"X\"}")).andExpect(status().isNotFound());
        como(admin, delete(FRANQUICIAS + "/32000")).andExpect(status().isNotFound());
    }

    // ---------- seguridad: matriz rol × endpoint ----------

    @Test
    void soloAdmin() throws Exception {
        for (UsuarioConToken u : new UsuarioConToken[] {vendedor, cliente}) {
            como(u, get(CATEGORIAS)).andExpect(status().isForbidden());
            como(u, post(CATEGORIAS).content("{\"titulo\":\"X\"}")).andExpect(status().isForbidden());
            como(u, put(CATEGORIAS + "/1").content("{\"titulo\":\"X\"}")).andExpect(status().isForbidden());
            como(u, delete(CATEGORIAS + "/1")).andExpect(status().isForbidden());
            como(u, get(FRANQUICIAS)).andExpect(status().isForbidden());
            como(u, post(FRANQUICIAS).content("{\"nombre\":\"X\"}")).andExpect(status().isForbidden());
            como(u, put(FRANQUICIAS + "/1").content("{\"nombre\":\"X\"}")).andExpect(status().isForbidden());
            como(u, delete(FRANQUICIAS + "/1")).andExpect(status().isForbidden());
        }
        mvc.perform(get(CATEGORIAS)).andExpect(status().isUnauthorized());
        mvc.perform(post(FRANQUICIAS).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"X\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(CATEGORIAS + "/1")).andExpect(status().isUnauthorized());
        como(admin, get(CATEGORIAS)).andExpect(status().isOk());
        como(admin, get(FRANQUICIAS)).andExpect(status().isOk());
    }
}
