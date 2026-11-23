package cl.primerplayer.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.io.ByteArrayInputStream;

import org.apache.poi.ss.usermodel.WorkbookFactory;
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
class ProductosAdminIntegracionTest {

    static final String BASE = "/api/v1/admin/productos";

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

    static String json(String nombre, String categoria, String codigo, Integer limite) {
        return """
            {"nombre":"%s","descripcion":"Sobre de 10 cartas","precio":4990,"categoria":"%s","tipo":"Sobre",
             "idioma":"Inglés","franquicia":"Pokémon","codigoBarras":%s,"limitePorUsuario":%s,"publicado":true,
             "imagenes":[{"url":"https://cdn.test/a.webp","portada":true},{"url":"https://cdn.test/b.webp","portada":false}]}
            """.formatted(nombre, categoria, codigo == null ? "null" : "\"" + codigo + "\"", limite);
    }

    private ResultActions como(UsuarioConToken u, MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.header(HttpHeaders.AUTHORIZATION, u.bearer()));
    }

    private long crear(String body) throws Exception {
        String r = como(vendedor, post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(r, "$.id")).longValue();
    }

    private static String unico() {
        return "ZX" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ---------- CRUD ----------

    @Test
    void crear_201ConStockCeroEtagYFormaDelFrontend() throws Exception {
        String ean = TokensDePrueba.ean13();
        como(vendedor, post(BASE).contentType(MediaType.APPLICATION_JSON).content(json("Sobre Black Bolt", "tcg-pokemon", ean, 4)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.stock").value(0))
                .andExpect(jsonPath("$.categoria").value("tcg-pokemon"))
                .andExpect(jsonPath("$.franquicia").value("Pokémon"))
                .andExpect(jsonPath("$.codigoBarras").value(ean))
                .andExpect(jsonPath("$.limitePorUsuario").value(4))
                .andExpect(jsonPath("$.imagen").value("https://cdn.test/a.webp"))
                .andExpect(jsonPath("$.imagenes", hasSize(2)))
                .andExpect(jsonPath("$.fecha").exists())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void flujoCompleto_obtenerActualizarPublicarEliminar() throws Exception {
        long id = crear(json("Figura Goku", "figuras-dragon-ball", null, null));

        como(vendedor, get(BASE + "/" + id)).andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ETAG, "\"0\""));

        como(vendedor, put(BASE + "/" + id).header(HttpHeaders.IF_MATCH, "\"0\"").contentType(MediaType.APPLICATION_JSON)
                .content(json("Figura Goku SSJ", "figuras-dragon-ball", null, null).replace("\"https://cdn.test/b.webp\",\"portada\":false", "\"https://cdn.test/c.webp\",\"portada\":false")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Figura Goku SSJ"))
                .andExpect(jsonPath("$.imagenes[1].url").value("https://cdn.test/c.webp"))
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""));

        como(vendedor, patch(BASE + "/" + id + "/publicacion").contentType(MediaType.APPLICATION_JSON).content("{\"publicado\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publicado").value(false));

        como(vendedor, delete(BASE + "/" + id)).andExpect(status().isNoContent());
        como(vendedor, get(BASE + "/" + id)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("item-unavailable"));
        assertThat(jdbc.queryForObject("SELECT eliminado_en IS NOT NULL FROM producto WHERE id = ?", Boolean.class, id)).isTrue();
    }

    // ---------- reglas ----------

    @Test
    void put_sinIfMatch_428YConVersionVieja_409VersionConflict() throws Exception {
        long id = crear(json("Funko Naruto", "figuras-funko-pop", null, null));
        String body = json("Funko Naruto 2", "figuras-funko-pop", null, null);

        como(vendedor, put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionRequired()).andExpect(jsonPath("$.code").value("precondition-required"));
        como(vendedor, put(BASE + "/" + id).header(HttpHeaders.IF_MATCH, "0").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        como(admin, put(BASE + "/" + id).header(HttpHeaders.IF_MATCH, "0").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("version-conflict"));
    }

    @Test
    void codigoDeBarras_repetido409_yVendedorNoLoCambia() throws Exception {
        String ean = TokensDePrueba.ean13();
        long id = crear(json("Juego A", "juegos-ps5", ean, null));
        como(vendedor, post(BASE).contentType(MediaType.APPLICATION_JSON).content(json("Juego B", "juegos-ps5", ean, null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("barcode-taken"));
        como(vendedor, put(BASE + "/" + id).header(HttpHeaders.IF_MATCH, "0").contentType(MediaType.APPLICATION_JSON)
                .content(json("Juego A", "juegos-ps5", TokensDePrueba.ean13(), null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("barcode-immutable"));
    }

    @Test
    void codigoDeBarras_vendedorNoLoAgrega_adminLoAgregaYLoCambia() throws Exception {
        long id = crear(json("Juego sin código", "juegos-switch", null, null));
        String primero = TokensDePrueba.ean13(), segundo = TokensDePrueba.ean13();

        como(vendedor, put(BASE + "/" + id).header(HttpHeaders.IF_MATCH, "0").contentType(MediaType.APPLICATION_JSON)
                .content(json("Juego sin código", "juegos-switch", primero, null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("barcode-immutable"));

        como(admin, put(BASE + "/" + id).header(HttpHeaders.IF_MATCH, "0").contentType(MediaType.APPLICATION_JSON)
                .content(json("Juego sin código", "juegos-switch", primero, null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.codigoBarras").value(primero));
        como(admin, put(BASE + "/" + id).header(HttpHeaders.IF_MATCH, "1").contentType(MediaType.APPLICATION_JSON)
                .content(json("Juego sin código", "juegos-switch", segundo, null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.codigoBarras").value(segundo));

        // El vendedor sigue pudiendo editar el resto si reenvía el mismo código
        como(vendedor, put(BASE + "/" + id).header(HttpHeaders.IF_MATCH, "2").contentType(MediaType.APPLICATION_JSON)
                .content(json("Juego con código", "juegos-switch", segundo, null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Juego con código"));
    }

    @Test
    void codigoDeBarras_adminNoPuedeUsarUnoYaAsignado_409BarcodeTaken() throws Exception {
        String ean = TokensDePrueba.ean13();
        crear(json("Con código", "juegos-ps4", ean, null));
        long id = crear(json("Sin código", "juegos-ps4", null, null));
        como(admin, put(BASE + "/" + id).header(HttpHeaders.IF_MATCH, "0").contentType(MediaType.APPLICATION_JSON)
                .content(json("Sin código", "juegos-ps4", ean, null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("barcode-taken"));
    }

    @Test
    void limiteConfigurado_vendedor403LimitAdminOnly_admin200() throws Exception {
        long id = crear(json("ETB Pokémon", "tcg-pokemon", null, 2));
        como(vendedor, put(BASE + "/" + id).header(HttpHeaders.IF_MATCH, "0").contentType(MediaType.APPLICATION_JSON)
                .content(json("ETB Pokémon", "tcg-pokemon", null, 5)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("limit-admin-only"));
        como(admin, put(BASE + "/" + id).header(HttpHeaders.IF_MATCH, "0").contentType(MediaType.APPLICATION_JSON)
                .content(json("ETB Pokémon", "tcg-pokemon", null, 5)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.limitePorUsuario").value(5));
    }

    @Test
    void validaciones_400() throws Exception {
        como(vendedor, post(BASE).contentType(MediaType.APPLICATION_JSON).content(json("X", "figuras", null, null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("categoria"));
        como(vendedor, post(BASE).contentType(MediaType.APPLICATION_JSON).content(json("X", "no-existe", null, null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
        como(vendedor, post(BASE).contentType(MediaType.APPLICATION_JSON).content(json("X", "tcg-pokemon", "7801234567891", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("codigoBarras"));
        como(vendedor, post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"precio\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("nombre", "precio", "categoria")));
    }

    // ---------- búsqueda e indicadores ----------

    @Test
    void buscar_porNombreSinTildes_categoriaAgrupadora_yEstado() throws Exception {
        String marca = unico();
        long figura = crear(json("Figura Pikachú " + marca, "figuras-pokemon", null, null));
        long sobre = crear(json("Sobre " + marca, "tcg-pokemon", null, null));
        jdbc.update("UPDATE producto SET stock = 2 WHERE id = ?", sobre);   // stock bajo (umbral 3)

        como(vendedor, get(BASE).param("q", "pikachu " + marca.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder((int) figura)))
                .andExpect(jsonPath("$.totalElements").value(1));
        como(vendedor, get(BASE).param("q", marca).param("categoria", "figuras"))
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder((int) figura)));
        como(vendedor, get(BASE).param("q", marca).param("estado", "stock-bajo"))
                .andExpect(jsonPath("$.content[*].id").value(containsInAnyOrder((int) sobre)));
        como(vendedor, get(BASE).param("q", marca).param("estado", "agotado").param("estado", "stock-bajo")
                        .param("sort", "nombre,asc"))
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.contains((int) figura, (int) sobre)));
        como(vendedor, get(BASE).param("q", String.valueOf(sobre)))
                .andExpect(jsonPath("$.content[*].id", org.hamcrest.Matchers.hasItem((int) sobre)));
        como(vendedor, get(BASE).param("sort", "popularidad,asc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("sort"));
    }

    @Test
    void indicadores_cuentanStockBajoYAgotados() throws Exception {
        String antes = como(vendedor, get(BASE + "/indicadores")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long sobre = crear(json("Sobre " + unico(), "tcg-pokemon", null, null));
        crear(json("Sobre " + unico(), "tcg-pokemon", null, null));
        jdbc.update("UPDATE producto SET stock = 1 WHERE id = ?", sobre);

        String despues = como(vendedor, get(BASE + "/indicadores")).andReturn().getResponse().getContentAsString();
        assertThat(((Number) JsonPath.read(despues, "$.total")).longValue() - ((Number) JsonPath.read(antes, "$.total")).longValue()).isEqualTo(2);
        assertThat(((Number) JsonPath.read(despues, "$.stockBajo")).longValue() - ((Number) JsonPath.read(antes, "$.stockBajo")).longValue()).isEqualTo(1);
        assertThat(((Number) JsonPath.read(despues, "$.agotados")).longValue() - ((Number) JsonPath.read(antes, "$.agotados")).longValue()).isEqualTo(1);
    }

    @Test
    void categoriasYFranquicias_publicas() throws Exception {
        mvc.perform(get("/api/v1/categorias")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug == 'figuras')].hijos[*].slug", org.hamcrest.Matchers.hasItem("figuras-naruto")));
        mvc.perform(get("/api/v1/franquicias")).andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasItem("Pokémon")));
    }

    @Test
    void exportarStock_jsonSinPaginarYExcelPorNegociacionSoloAdmin() throws Exception {
        crear(json("Exportar " + unico(), "tcg-pokemon", null, null));
        int activos = jdbc.queryForObject("SELECT count(*) FROM producto WHERE eliminado_en IS NULL", Integer.class);

        var json = como(admin, get(BASE + "/exportar").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(json, "$.length()")).isEqualTo(activos);

        var respuesta = como(admin, get(BASE + "/exportar")
                        .accept("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"stock-productos.xlsx\""))
                .andReturn().getResponse();
        assertThat(respuesta.getContentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(respuesta.getContentAsByteArray()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            assertThat(workbook.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(activos + 1);
        }

        mvc.perform(get(BASE + "/exportar")).andExpect(status().isUnauthorized());
        como(vendedor, get(BASE + "/exportar")).andExpect(status().isForbidden());
    }

    // ---------- matriz rol × endpoint ----------

    @Test
    void seguridad_anonimo401_cliente403_vendedorYAdminPasan() throws Exception {
        long id = crear(json("Seguridad " + unico(), "tcg-pokemon", null, null));
        String body = json("Seguridad", "tcg-pokemon", null, null);
        var endpoints = java.util.List.<java.util.function.Supplier<MockHttpServletRequestBuilder>>of(
                () -> get(BASE),
                () -> get(BASE + "/indicadores"),
                () -> get(BASE + "/" + id),
                () -> post(BASE).contentType(MediaType.APPLICATION_JSON).content(body),
                () -> put(BASE + "/" + id).header(HttpHeaders.IF_MATCH, "999").contentType(MediaType.APPLICATION_JSON).content(body),
                () -> patch(BASE + "/" + id + "/publicacion").contentType(MediaType.APPLICATION_JSON).content("{\"publicado\":true}"),
                () -> delete(BASE + "/999999999"));

        for (int i = 0; i < endpoints.size(); i++) {
            var e = endpoints.get(i);
            mvc.perform(e.get()).andExpect(status().isUnauthorized());
            como(cliente, e.get()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
            for (var interno : new UsuarioConToken[] {vendedor, admin}) {
                int s = como(interno, e.get()).andReturn().getResponse().getStatus();
                assertThat(s).as("endpoint #" + i).isNotIn(401, 403);
            }
        }
    }
}
