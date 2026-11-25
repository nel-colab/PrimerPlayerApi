package cl.primerplayer.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
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

/** Autorización previa: lo que crea o cambia un VENDEDOR no llega a la tienda hasta que un ADMIN lo aprueba. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AprobacionProductosIntegracionTest {

    static final String BASE = "/api/v1/admin/productos";
    static final String REVISIONES = BASE + "/revisiones";

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

    static String json(String nombre, long precio, String codigo, boolean publicado) {
        return """
            {"nombre":"%s","precio":%d,"categoria":"tcg","codigoBarras":%s,"publicado":%s,
             "imagenes":[{"url":"https://cdn.test/a.webp","portada":true}]}
            """.formatted(nombre, precio, codigo == null ? "null" : "\"" + codigo + "\"", publicado);
    }

    private ResultActions como(UsuarioConToken u, MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.header(HttpHeaders.AUTHORIZATION, u.bearer()));
    }

    private long crear(UsuarioConToken u, String body) throws Exception {
        String r = como(u, post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(r, "$.id")).longValue();
    }

    /** Id de la revisión pendiente del producto (la primera del historial). */
    private long pendiente(long productoId) throws Exception {
        String r = como(admin, get(BASE + "/" + productoId + "/revisiones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(r, "$[0].id")).longValue();
    }

    private ResultActions putComo(UsuarioConToken u, long id, int version, String body) throws Exception {
        return como(u, put(BASE + "/" + id).header(HttpHeaders.IF_MATCH, "\"" + version + "\"")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String nombre() {
        return "Aprob " + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    // ---------- alta ----------

    @Test
    void altaDeVendedor_quedaPendienteSinPublicar_noSeVende_peroAceptaStock() throws Exception {
        String ean = TokensDePrueba.ean13();
        String body = json(nombre(), 4990, ean, true);
        var r = como(vendedor, post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aprobacion").value("PENDIENTE"))
                .andExpect(jsonPath("$.publicado").value(false))
                .andExpect(jsonPath("$.cambioPendiente").value(false))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(r, "$.id")).longValue();

        // Tienda web y POS: no existe para la venta
        mvc.perform(get("/api/v1/productos/" + id)).andExpect(status().isNotFound());
        como(vendedor, get("/api/v1/admin/pos/productos/codigo/" + ean)).andExpect(status().isNotFound());

        // Stock: se puede cargar mientras espera
        como(vendedor, post("/api/v1/admin/inventario/" + id + "/ajustes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\":5,\"motivo\":\"Ingreso\"}")).andExpect(status().isOk());

        // Bandeja del ADMIN
        como(admin, get(REVISIONES).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.productoId == " + id + ")].tipo", contains("ALTA")))
                .andExpect(jsonPath("$.content[?(@.productoId == " + id + ")].solicitadoPor",
                        contains((int) vendedor.id())));

        // El vendedor no puede publicarlo por su cuenta
        como(vendedor, patch(BASE + "/" + id + "/publicacion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"publicado\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("product-not-approved"));
    }

    @Test
    void altaDeAdmin_naceAprobada_ySinRevision() throws Exception {
        long id = crear(admin, json(nombre(), 4990, null, true));
        como(admin, get(BASE + "/" + id))
                .andExpect(jsonPath("$.aprobacion").value("APROBADO"))
                .andExpect(jsonPath("$.publicado").value(true));
        como(admin, get(BASE + "/" + id + "/revisiones")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void aprobarAlta_publicaElProducto_ySegundaAprobacion409() throws Exception {
        long id = crear(vendedor, json(nombre(), 4990, null, true));
        long rev = pendiente(id);

        como(admin, post(REVISIONES + "/" + rev + "/aprobacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.revisadoPor").value((int) admin.id()))
                .andExpect(jsonPath("$.actual.aprobacion").value("APROBADO"))
                .andExpect(jsonPath("$.actual.publicado").value(true));

        mvc.perform(get("/api/v1/productos/" + id)).andExpect(status().isOk());
        como(admin, post(REVISIONES + "/" + rev + "/aprobacion"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("review-not-pending"));
    }

    @Test
    void rechazarAlta_conMotivo_yAlCorregirVuelveAPendiente() throws Exception {
        long id = crear(vendedor, json(nombre(), 4990, null, false));
        long rev = pendiente(id);

        como(admin, post(REVISIONES + "/" + rev + "/rechazo").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        como(admin, post(REVISIONES + "/" + rev + "/rechazo").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Falta la foto real\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.motivoRechazo").value("Falta la foto real"))
                .andExpect(jsonPath("$.actual.aprobacion").value("RECHAZADO"));

        // El vendedor lo corrige: se aplica (no está a la venta) y vuelve a la bandeja
        putComo(vendedor, id, 1, json("Corregido " + nombre(), 4990, null, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprobacion").value("PENDIENTE"))
                .andExpect(jsonPath("$.publicado").value(false));
        como(vendedor, get(BASE + "/" + id + "/revisiones"))
                .andExpect(jsonPath("$[*].estado", contains("PENDIENTE", "RECHAZADA")));
    }

    @Test
    void adminQuePublicaUnPendiente_loAprueba_yCierraLaRevision() throws Exception {
        long id = crear(vendedor, json(nombre(), 4990, null, false));
        pendiente(id);
        como(admin, patch(BASE + "/" + id + "/publicacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicado\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprobacion").value("APROBADO"))
                .andExpect(jsonPath("$.publicado").value(true));
        como(admin, get(BASE + "/" + id + "/revisiones"))
                .andExpect(jsonPath("$[0].estado").value("APROBADA"))
                .andExpect(jsonPath("$[0].revisadoPor").value((int) admin.id()));
    }

    // ---------- edición de un producto aprobado ----------

    @Test
    void edicionDeVendedor_quedaPropuesta_laTiendaSigueConLoAprobado_yAlAprobarSeAplica() throws Exception {
        String n = nombre();
        long id = crear(admin, json(n, 4990, null, true));

        putComo(vendedor, id, 0, json(n, 5990, null, true))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.precio").value(4990))
                .andExpect(jsonPath("$.cambioPendiente").value(true));
        mvc.perform(get("/api/v1/productos/" + id)).andExpect(jsonPath("$.precio").value(4990));

        // Una nueva edición reemplaza la propuesta pendiente (sigue habiendo una sola)
        putComo(vendedor, id, 0, json(n, 6990, null, true)).andExpect(status().isAccepted());
        como(admin, get(BASE + "/" + id + "/revisiones"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tipo").value("EDICION"))
                .andExpect(jsonPath("$[0].propuesto.precio").value(6990))
                .andExpect(jsonPath("$[0].actual.precio").value(4990))
                .andExpect(jsonPath("$[0].camposModificados", contains("precio")))
                .andExpect(jsonPath("$[0].desactualizada").value(false));

        como(admin, post(REVISIONES + "/" + pendiente(id) + "/aprobacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actual.precio").value(6990))
                .andExpect(jsonPath("$.actual.cambioPendiente").value(false))
                .andExpect(jsonPath("$.actual.version").value(1));
        mvc.perform(get("/api/v1/productos/" + id)).andExpect(jsonPath("$.precio").value(6990));
    }

    @Test
    void edicionIgualALoAprobado_noCreaRevision_yDescartaLaPendiente() throws Exception {
        String n = nombre();
        long id = crear(admin, json(n, 4990, null, true));
        putComo(vendedor, id, 0, json(n, 5990, null, true)).andExpect(status().isAccepted());

        putComo(vendedor, id, 0, json(n, 4990, null, true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cambioPendiente").value(false));
        como(admin, get(BASE + "/" + id + "/revisiones")).andExpect(jsonPath("$[0].estado").value("DESCARTADA"));
    }

    @Test
    void edicionDeVendedor_respetaLasReglasDelRolAlProponer() throws Exception {
        String ean = TokensDePrueba.ean13();
        long id = crear(admin, json(nombre(), 4990, ean, true));
        putComo(vendedor, id, 0, json(nombre(), 4990, TokensDePrueba.ean13(), true))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("barcode-immutable"));
        putComo(vendedor, id, 5, json(nombre(), 4990, ean, true))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("version-conflict"));
    }

    @Test
    void edicionDesactualizada_noSeAprueba_reviewOutdated_peroSePuedeRechazar() throws Exception {
        String n = nombre();
        long id = crear(admin, json(n, 4990, null, true));
        putComo(vendedor, id, 0, json(n, 5990, null, true)).andExpect(status().isAccepted());
        long rev = pendiente(id);

        // Un ADMIN cambia el producto directo: la propuesta quedó sobre una versión vieja
        putComo(admin, id, 0, json(n + " v2", 4990, null, true)).andExpect(status().isOk());
        como(admin, get(BASE + "/" + id + "/revisiones")).andExpect(jsonPath("$[0].desactualizada").value(true));

        como(admin, post(REVISIONES + "/" + rev + "/aprobacion"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("review-outdated"));
        como(admin, post(REVISIONES + "/" + rev + "/rechazo").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Propónlo de nuevo sobre el nombre nuevo\"}"))
                .andExpect(status().isOk());
        como(admin, get(BASE + "/" + id))
                .andExpect(jsonPath("$.nombre").value(n + " v2"))
                .andExpect(jsonPath("$.precio").value(4990));
    }

    @Test
    void rechazarEdicion_elProductoNoCambia() throws Exception {
        String n = nombre();
        long id = crear(admin, json(n, 4990, null, true));
        putComo(vendedor, id, 0, json(n, 1, null, true)).andExpect(status().isAccepted());

        como(admin, post(REVISIONES + "/" + pendiente(id) + "/rechazo").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Precio incorrecto\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actual.precio").value(4990))
                .andExpect(jsonPath("$.actual.aprobacion").value("APROBADO"))
                .andExpect(jsonPath("$.actual.cambioPendiente").value(false));
    }

    // ---------- aprobar con cambios ----------

    private ResultActions aprobarConCambios(long revision, String datos, int version) throws Exception {
        return como(admin, post(REVISIONES + "/" + revision + "/aprobacion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"datos\":" + datos + ",\"version\":" + version + "}"));
    }

    @Test
    void altaAprobadaConCambios_quedaComoLaDejoElAdmin_yElHistorialGuardaAmbasVersiones() throws Exception {
        String n = nombre();
        long id = crear(vendedor, json(n, 4990, null, true));
        long rev = pendiente(id);

        aprobarConCambios(rev, json(n + " (corregido)", 5990, null, true), 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.actual.nombre").value(n + " (corregido)"))
                .andExpect(jsonPath("$.actual.precio").value(5990))
                .andExpect(jsonPath("$.actual.aprobacion").value("APROBADO"))
                .andExpect(jsonPath("$.actual.publicado").value(true))
                .andExpect(jsonPath("$.aprobado.precio").value(5990));
        mvc.perform(get("/api/v1/productos/" + id)).andExpect(jsonPath("$.precio").value(5990));
    }

    @Test
    void edicionAprobadaConCambios_aplicaLoDelAdmin_yConservaLoPropuesto() throws Exception {
        String n = nombre();
        long id = crear(admin, json(n, 4990, null, true));
        putComo(vendedor, id, 0, json(n, 5990, null, true)).andExpect(status().isAccepted());

        aprobarConCambios(pendiente(id), json(n, 5490, null, true), 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actual.precio").value(5490))
                .andExpect(jsonPath("$.propuesto.precio").value(5990))
                .andExpect(jsonPath("$.aprobado.precio").value(5490))
                .andExpect(jsonPath("$.actual.cambioPendiente").value(false));
        // Aprobar tal cual no guarda "aprobado"
        long otro = crear(admin, json(nombre(), 4990, null, true));
        putComo(vendedor, otro, 0, json(nombre(), 4990, null, true)).andExpect(status().isAccepted());
        como(admin, post(REVISIONES + "/" + pendiente(otro) + "/aprobacion"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.aprobado").doesNotExist());
    }

    @Test
    void edicionDesactualizada_sePuedeAprobarConCambiosSobreLaVersionActual() throws Exception {
        String n = nombre();
        long id = crear(admin, json(n, 4990, null, true));
        putComo(vendedor, id, 0, json(n, 5990, null, true)).andExpect(status().isAccepted());
        long rev = pendiente(id);
        putComo(admin, id, 0, json(n + " v2", 4990, null, true)).andExpect(status().isOk());   // versión 1

        aprobarConCambios(rev, json(n + " v2", 5990, null, true), 0)   // corrigió mirando la versión vieja
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("version-conflict"));
        aprobarConCambios(rev, json(n + " v2", 5990, null, true), 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actual.nombre").value(n + " v2"))
                .andExpect(jsonPath("$.actual.precio").value(5990));
    }

    @Test
    void aprobarConCambios_reglasDeAdmin_yValidaElCuerpo() throws Exception {
        long id = crear(vendedor, json(nombre(), 4990, null, false));
        long rev = pendiente(id);
        String ean = TokensDePrueba.ean13();

        aprobarConCambios(rev, "{\"nombre\":\"\",\"precio\":-1}", 0).andExpect(status().isBadRequest());
        como(admin, post(REVISIONES + "/" + rev + "/aprobacion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"datos\":" + json(nombre(), 4990, null, true) + "}")).andExpect(status().isBadRequest());
        // El ADMIN sí puede poner el código de barras al aprobar; y puede dejarlo sin publicar
        aprobarConCambios(rev, json(nombre(), 4990, ean, false), 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actual.codigoBarras").value(ean))
                .andExpect(jsonPath("$.actual.aprobacion").value("APROBADO"))
                .andExpect(jsonPath("$.actual.publicado").value(false));
    }

    // ---------- publicación ----------

    @Test
    void vendedorOcultaDirecto_peroRepublicarEsperaAprobacion() throws Exception {
        long id = crear(admin, json(nombre(), 4990, null, true));

        como(vendedor, patch(BASE + "/" + id + "/publicacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicado\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publicado").value(false));
        como(vendedor, patch(BASE + "/" + id + "/publicacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicado\":true}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.publicado").value(false))
                .andExpect(jsonPath("$.cambioPendiente").value(true));

        como(admin, post(REVISIONES + "/" + pendiente(id) + "/aprobacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actual.publicado").value(true));
    }

    @Test
    void eliminar_descartaLaRevisionPendiente() throws Exception {
        long id = crear(vendedor, json(nombre(), 4990, null, false));
        long rev = pendiente(id);
        como(vendedor, delete(BASE + "/" + id)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT estado FROM producto_revision WHERE id = ?", String.class, rev))
                .isEqualTo("DESCARTADA");
        como(admin, post(REVISIONES + "/" + rev + "/aprobacion"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("review-not-pending"));
    }

    // ---------- base de datos ----------

    @Test
    void laBaseRechazaPublicarUnProductoNoAprobado_yDosPendientesDelMismoProducto() throws Exception {
        long id = crear(vendedor, json(nombre(), 4990, null, false));
        assertThatThrownBy(() -> jdbc.update("UPDATE producto SET publicado = TRUE WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO producto_revision (producto_id, tipo, version_base, solicitado_por)
                VALUES (?, 'ALTA', 0, ?)""", id, vendedor.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- matriz rol × endpoint ----------

    @Test
    void seguridad_bandejaEHistorialVendedorYAdmin_aprobarYRechazarSoloAdmin() throws Exception {
        long id = crear(vendedor, json(nombre(), 4990, null, false));
        long rev = pendiente(id);

        List<Supplier<MockHttpServletRequestBuilder>> lectura = List.of(
                () -> get(REVISIONES),
                () -> get(BASE + "/" + id + "/revisiones"));
        List<Supplier<MockHttpServletRequestBuilder>> soloAdmin = List.of(
                () -> post(REVISIONES + "/999999999/aprobacion"),
                () -> post(REVISIONES + "/" + rev + "/rechazo").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"x\"}"));

        for (var e : lectura) {
            mvc.perform(e.get()).andExpect(status().isUnauthorized());
            como(cliente, e.get()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
            como(vendedor, e.get()).andExpect(status().isOk());
            como(admin, e.get()).andExpect(status().isOk());
        }
        for (var e : soloAdmin) {
            mvc.perform(e.get()).andExpect(status().isUnauthorized());
            como(cliente, e.get()).andExpect(status().isForbidden());
            como(vendedor, e.get()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
            int s = como(admin, e.get()).andReturn().getResponse().getStatus();
            assertThat(s).isNotIn(401, 403);
        }
        como(admin, get(REVISIONES).param("estado", "otro"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("estado"));
        como(admin, get(REVISIONES).param("estado", "todas"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", hasItem((int) rev)));
    }
}
