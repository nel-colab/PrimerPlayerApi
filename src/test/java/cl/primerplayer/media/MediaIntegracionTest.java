package cl.primerplayer.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import cl.primerplayer.TestcontainersConfiguration;
import cl.primerplayer.TokensDePrueba;
import cl.primerplayer.TokensDePrueba.UsuarioConToken;
import cl.primerplayer.shared.api.Rol;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AlmacenEnMemoria.Config.class})
@ActiveProfiles("test")
class MediaIntegracionTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired AlmacenEnMemoria almacen;

    UsuarioConToken vendedor, cliente;

    @BeforeEach
    void preparar() {
        vendedor = TokensDePrueba.crear(jdbc, encoder, Rol.VENDEDOR);
        cliente = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
        almacen.caido = false;
    }

    static byte[] png(int ancho, int alto) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    ResultActions subir(UsuarioConToken u, String ruta, byte[] contenido, String nombre) throws Exception {
        var req = multipart("/api/v1/admin/media/" + ruta).file(new MockMultipartFile("archivo", nombre, "application/octet-stream", contenido));
        return mvc.perform(u == null ? req : req.header(HttpHeaders.AUTHORIZATION, u.bearer()));
    }

    String idDe(ResultActions r) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void imagen_seSubeYQuedaPublica() throws Exception {
        String id = idDe(subir(vendedor, "imagenes", png(40, 30), "foto.cualquier-extension")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url", startsWith("https://media.test/public-media/imagenes/")))
                .andExpect(jsonPath("$.mime").value("image/png"))
                .andExpect(jsonPath("$.width").value(40))
                .andExpect(jsonPath("$.height").value(30)));
        assertThat(jdbc.queryForObject("SELECT bucket FROM archivo WHERE id = ?::uuid", String.class, id)).isEqualTo("public-media");
        assertThat(jdbc.queryForObject("SELECT subido_por FROM archivo WHERE id = ?::uuid", Long.class, id)).isEqualTo(vendedor.id());
    }

    @Test
    void archivosInvalidos_400() throws Exception {
        subir(vendedor, "imagenes", "no soy una imagen".getBytes(), "falsa.png")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
        subir(vendedor, "imagenes", new byte[0], "vacia.png").andExpect(status().isBadRequest());
        byte[] grande = new byte[(int) (5.5 * 1024 * 1024)];
        System.arraycopy(png(2, 2), 0, grande, 0, 8);   // firma PNG, pero supera 5 MB
        subir(vendedor, "imagenes", grande, "grande.png").andExpect(status().isBadRequest());
    }

    @Test
    void almacenamientoCaido_503YNadaRegistrado() throws Exception {
        long antes = jdbc.queryForObject("SELECT count(*) FROM archivo", Long.class);
        almacen.caido = true;
        subir(vendedor, "imagenes", png(5, 5), "a.png")
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("storage-unavailable"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM archivo", Long.class)).isEqualTo(antes);
    }

    @Test
    void comprobante_privadoConUrlFirmada() throws Exception {
        String id = idDe(subir(vendedor, "comprobantes", png(10, 10), "transferencia.png")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").doesNotExist()));
        mvc.perform(get("/api/v1/admin/media/comprobantes/" + id).header(HttpHeaders.AUTHORIZATION, vendedor.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", startsWith("https://media.test/firmado/private-docs/comprobantes/")))
                .andExpect(jsonPath("$.expiresAt").exists());

        // Una imagen pública no se entrega como comprobante
        String imagen = idDe(subir(vendedor, "imagenes", png(5, 5), "a.png"));
        mvc.perform(get("/api/v1/admin/media/comprobantes/" + imagen).header(HttpHeaders.AUTHORIZATION, vendedor.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void producto_conImagenSubida_yNuncaConUnComprobante() throws Exception {
        String imagen = idDe(subir(vendedor, "imagenes", png(8, 8), "a.png"));
        String comprobante = idDe(subir(vendedor, "comprobantes", png(8, 8), "c.png"));
        String body = """
                {"nombre":"Figura con foto","precio":19990,"categoria":"figuras-pokemon","publicado":true,
                 "imagenes":[{"archivoId":"%s","portada":true},{"url":"https://cdn.test/extra.webp","portada":false}]}""";

        mvc.perform(post("/api/v1/admin/productos").header(HttpHeaders.AUTHORIZATION, vendedor.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted(imagen)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imagen", startsWith("https://media.test/public-media/")))
                .andExpect(jsonPath("$.imagenes[0].archivoId").value(imagen))
                .andExpect(jsonPath("$.imagenes[1].url").value("https://cdn.test/extra.webp"));

        mvc.perform(post("/api/v1/admin/productos").header(HttpHeaders.AUTHORIZATION, vendedor.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted(comprobante)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
        mvc.perform(post("/api/v1/admin/productos").header(HttpHeaders.AUTHORIZATION, vendedor.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pos_cobraConTransferenciaUsandoUnComprobanteSubido() throws Exception {
        String comprobante = idDe(subir(vendedor, "comprobantes", png(8, 8), "c.png"));
        long producto = jdbc.queryForObject("""
                INSERT INTO producto (nombre, precio, categoria_id, stock, publicado)
                VALUES ('Sobre transferencia', 4990, (SELECT id FROM categoria WHERE slug = 'tcg-pokemon'), 3, true) RETURNING id
                """, Long.class);
        mvc.perform(post("/api/v1/admin/pos/ventas").header(HttpHeaders.AUTHORIZATION, vendedor.bearer())
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lineas":[{"type":"product","itemId":%d,"quantity":1}],
                                 "pagos":[{"method":"transferencia","amount":4990,"details":{"transferProofId":"%s"}}]}"""
                                .formatted(producto, comprobante)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payments[0].details.transferProofId").value(comprobante));
    }

    @Test
    void seguridad() throws Exception {
        subir(null, "imagenes", png(2, 2), "a.png").andExpect(status().isUnauthorized());
        subir(cliente, "imagenes", png(2, 2), "a.png").andExpect(status().isForbidden());
        subir(cliente, "comprobantes", png(2, 2), "a.png").andExpect(status().isForbidden());
    }
}
