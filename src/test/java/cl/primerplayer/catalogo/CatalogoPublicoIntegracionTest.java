package cl.primerplayer.catalogo;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import cl.primerplayer.TestcontainersConfiguration;
import cl.primerplayer.TokensDePrueba;
import cl.primerplayer.TokensDePrueba.UsuarioConToken;
import cl.primerplayer.shared.api.Rol;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CatalogoPublicoIntegracionTest {

    static final String BASE = "/api/v1/productos";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;

    /** Marca única por prueba: la base es compartida, así cada prueba solo ve sus productos. */
    String marca;

    @BeforeEach
    void marcaUnica() {
        marca = "M-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Inserta directo en la base: stock y popularidad no se escriben desde el catálogo. */
    long producto(String nombre, String categoria, String tipo, String idioma, String franquicia, long precio,
                  int stock, int popularidad, boolean publicado, int diasDesdeIngreso) {
        Long id = jdbc.queryForObject("""
                INSERT INTO producto (nombre, precio, categoria_id, tipo, idioma, marca, franquicia_id, stock,
                                      popularidad, publicado, fecha_ingreso, limite_por_cliente)
                VALUES (?, ?, (SELECT id FROM categoria WHERE slug = ?), ?, ?, ?,
                        (SELECT id FROM franquicia WHERE nombre = ?), ?, ?, ?, current_date - ?, 2)
                RETURNING id
                """, Long.class, nombre, precio, categoria, tipo, idioma, marca, franquicia, stock, popularidad,
                publicado, diasDesdeIngreso);
        return id;
    }

    long visible(String nombre, String categoria, String tipo, long precio, int stock) {
        return producto(nombre, categoria, tipo, "Español", "Pokémon", precio, stock, 10, true, 5);
    }

    // ---------- GET /productos ----------

    @Test
    void buscar_soloPublicadosYNoEliminados() throws Exception {
        long publicado = visible("Sobre visible", "tcg", "sobres", 4990, 5);
        long oculto = producto("Sobre oculto", "tcg", "sobres", "Español", "Pokémon", 4990, 5, 10, false, 5);
        long eliminado = visible("Sobre eliminado", "tcg", "sobres", 4990, 5);
        jdbc.update("UPDATE producto SET eliminado_en = now() WHERE id = ?", eliminado);

        mvc.perform(get(BASE).param("marca", marca))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[*].id", contains((int) publicado)))
                .andExpect(jsonPath("$.content[*].id", not(hasItem((int) oculto))))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(24))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void buscar_formaDelProductDelFrontend() throws Exception {
        long id = visible("Elite Trainer Box", "tcg", "cajas", 59990, 3);
        jdbc.update("INSERT INTO producto_imagen (producto_id, posicion, url_externa, es_portada) VALUES (?, 0, ?, false), (?, 1, ?, true)",
                id, "https://cdn.test/b.webp", id, "https://cdn.test/a.webp");

        mvc.perform(get(BASE).param("marca", marca))
                .andExpect(jsonPath("$.content[0].id").value((int) id))
                .andExpect(jsonPath("$.content[0].precio").value(59990))
                .andExpect(jsonPath("$.content[0].categoria").value("tcg"))
                .andExpect(jsonPath("$.content[0].franquicia").value("Pokémon"))
                .andExpect(jsonPath("$.content[0].limitePorUsuario").value(2))
                .andExpect(jsonPath("$.content[0].stock").value(3))
                .andExpect(jsonPath("$.content[0].fecha").exists())
                .andExpect(jsonPath("$.content[0].imagen").value("https://cdn.test/a.webp"))
                .andExpect(jsonPath("$.content[0].imagenes", contains("https://cdn.test/a.webp", "https://cdn.test/b.webp")))
                .andExpect(jsonPath("$.content[0].codigoBarras").doesNotExist())
                .andExpect(jsonPath("$.content[0].version").doesNotExist());
    }

    @Test
    void buscar_porCategoriaGeneral_yTodosNoFiltra() throws Exception {
        long naruto = visible("Figura Naruto", "figuras", "figura", 24990, 2);
        long goku = visible("Figura Goku", "figuras", "figura", 28990, 2);
        long sobre = visible("Sobre", "tcg", "sobres", 4990, 2);

        mvc.perform(get(BASE).param("marca", marca).param("categoria", "figuras"))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder((int) naruto, (int) goku)));
        mvc.perform(get(BASE).param("marca", marca).param("categoria", "todos"))
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get(BASE).param("marca", marca).param("categoria", "no-existe"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(BASE).param("marca", marca).param("categoria", "tcg"))
                .andExpect(jsonPath("$.content[*].id", contains((int) sobre)));
    }

    @Test
    void buscar_textoSinTildesNiMayusculas() throws Exception {
        long id = visible("Peluche Pokémon Ñandú", "figuras", "peluche", 16990, 4);
        visible("Mazo Charizard", "tcg", "mazos", 24990, 4);

        mvc.perform(get(BASE).param("marca", marca).param("q", "POKEMON nandu"))
                .andExpect(jsonPath("$.content[*].id", contains((int) id)));
    }

    @Test
    void buscar_enStockYRangoDePrecio() throws Exception {
        long barato = visible("Barato", "tcg", "sobres", 2990, 1);
        visible("Agotado", "tcg", "sobres", 3990, 0);
        long medio = visible("Medio", "tcg", "sobres", 9990, 7);
        visible("Caro", "tcg", "cajas", 59990, 2);

        mvc.perform(get(BASE).param("marca", marca).param("enStock", "true").param("precioMax", "10000"))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder((int) barato, (int) medio)));
        mvc.perform(get(BASE).param("marca", marca).param("precioMin", "3000").param("precioMax", "10000"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void buscar_ordenesDeLaListaBlanca() throws Exception {
        long a = producto("A", "tcg", "sobres", "Español", null, 9990, 3, 50, true, 30);
        long b = producto("B", "tcg", "sobres", "Español", null, 1990, 3, 90, true, 1);
        long c = producto("C", "tcg", "sobres", "Español", null, 4990, 3, 10, true, 10);

        mvc.perform(get(BASE).param("marca", marca))   // recomendados por defecto
                .andExpect(jsonPath("$.content[*].id", contains((int) b, (int) a, (int) c)));
        mvc.perform(get(BASE).param("marca", marca).param("sort", "recientes"))
                .andExpect(jsonPath("$.content[*].id", contains((int) b, (int) c, (int) a)));
        mvc.perform(get(BASE).param("marca", marca).param("sort", "precio-asc"))
                .andExpect(jsonPath("$.content[*].id", contains((int) b, (int) c, (int) a)));
        mvc.perform(get(BASE).param("marca", marca).param("sort", "precio-desc"))
                .andExpect(jsonPath("$.content[*].id", contains((int) a, (int) c, (int) b)));
    }

    @Test
    void buscar_paginacion() throws Exception {
        for (int i = 0; i < 5; i++) {
            visible("P" + i, "tcg", "sobres", 1000 + i, 1);
        }
        mvc.perform(get(BASE).param("marca", marca).param("page", "2").param("size", "2"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void buscar_facetasSinSuPropioFiltro() throws Exception {
        visible("S1", "tcg", "sobres", 4990, 1);
        visible("S2", "tcg", "sobres", 6990, 1);
        visible("C1", "tcg", "cajas", 59990, 1);
        producto("I1", "tcg", "sobres", "Inglés", "One Piece", 3990, 1, 10, true, 5);

        // Filtrando tipo=sobres: la faceta "tipo" sigue mostrando "cajas" (no aplica su propio filtro),
        // pero las demás facetas y el rango de precio sí respetan tipo=sobres.
        mvc.perform(get(BASE).param("marca", marca).param("tipo", "sobres"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.facets.tipo[0].valor").value("sobres"))
                .andExpect(jsonPath("$.facets.tipo[0].cantidad").value(3))
                .andExpect(jsonPath("$.facets.tipo[1].valor").value("cajas"))
                .andExpect(jsonPath("$.facets.tipo[1].cantidad").value(1))
                .andExpect(jsonPath("$.facets.idioma[0].valor").value("Español"))
                .andExpect(jsonPath("$.facets.idioma[0].cantidad").value(2))
                .andExpect(jsonPath("$.facets.idioma[1].valor").value("Inglés"))
                .andExpect(jsonPath("$.facets.franquicia[*].valor", containsInAnyOrder("Pokémon", "One Piece")))
                .andExpect(jsonPath("$.facets.precio.min").value(3990))
                .andExpect(jsonPath("$.facets.precio.max").value(6990));

        // OR dentro de la faceta, AND entre facetas
        mvc.perform(get(BASE).param("marca", marca).param("tipo", "sobres", "cajas").param("idioma", "Español"))
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get(BASE).param("marca", marca).param("franquicia", "One Piece"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void buscar_parametrosInvalidos_400Validation() throws Exception {
        mvc.perform(get(BASE).param("sort", "stock,desc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
        mvc.perform(get(BASE).param("precioMin", "5000").param("precioMax", "1000"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
        mvc.perform(get(BASE).param("size", "500"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
    }

    // ---------- destacados, detalle y relacionados ----------

    @Test
    void destacados_porPopularidadYSoloVisibles() throws Exception {
        long top = producto("Top", "tcg", "sobres", "Español", null, 4990, 3, 10_000_001, true, 5);
        long segundo = producto("Segundo", "tcg", "sobres", "Español", null, 4990, 3, 10_000_000, true, 5);
        producto("Oculto popular", "tcg", "sobres", "Español", null, 4990, 3, 20_000_000, false, 5);

        mvc.perform(get(BASE + "/destacados").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains((int) top, (int) segundo)));
        mvc.perform(get(BASE + "/destacados").param("limit", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
    }

    @Test
    void detalle_visible200_ocultoOEliminado404ItemUnavailable() throws Exception {
        long visible = visible("Visible", "tcg", "sobres", 4990, 3);
        long oculto = producto("Oculto", "tcg", "sobres", "Español", null, 4990, 3, 1, false, 5);
        long eliminado = visible("Eliminado", "tcg", "sobres", 4990, 3);
        jdbc.update("UPDATE producto SET eliminado_en = now() WHERE id = ?", eliminado);

        mvc.perform(get(BASE + "/" + visible)).andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Visible"));
        for (long id : new long[] {oculto, eliminado, 999_999_999L}) {
            mvc.perform(get(BASE + "/" + id))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("item-unavailable"));
        }
    }

    @Test
    void relacionados_mismaCategoriaPrimeroLuegoFranquicia() throws Exception {
        long base = producto("Base", "figuras", "figura", "Español", "Pokémon", 19990, 3, 10, true, 5);
        long mismaCategoria = producto("Misma cat", "figuras", "figura", "Español", "Naruto", 19990, 3, 5_000_000, true, 5);
        long mismaFranquicia = producto("Misma franq", "tcg", "sobres", "Español", "Pokémon", 4990, 3, 999_999, true, 5);
        long oculto = producto("Oculto", "figuras", "figura", "Español", "Pokémon", 19990, 3, 10, false, 5);

        mvc.perform(get(BASE + "/" + base + "/relacionados").param("limit", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value((int) mismaCategoria))
                .andExpect(jsonPath("$[*].id", hasItem((int) mismaFranquicia)))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) base))))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) oculto))));
        mvc.perform(get(BASE + "/" + oculto + "/relacionados"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("item-unavailable"));
    }

    // ---------- seguridad: rol × endpoint ----------

    @Test
    void seguridad_todosLosRolesYAnonimoPuedenLeerElCatalogo() throws Exception {
        long id = visible("Público", "tcg", "sobres", 4990, 3);
        UsuarioConToken[] usuarios = {
                null,
                TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE),
                TokensDePrueba.crear(jdbc, encoder, Rol.VENDEDOR),
                TokensDePrueba.crear(jdbc, encoder, Rol.ADMIN)};
        String[] rutas = {BASE, BASE + "/destacados", BASE + "/" + id, BASE + "/" + id + "/relacionados"};

        for (UsuarioConToken u : usuarios) {
            for (String ruta : rutas) {
                MockHttpServletRequestBuilder req = get(ruta);
                if (u != null) {
                    req = req.header(HttpHeaders.AUTHORIZATION, u.bearer());
                }
                mvc.perform(req).andExpect(status().isOk());
            }
        }
    }
}
