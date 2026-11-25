package cl.primerplayer.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.springframework.transaction.support.TransactionTemplate;

import cl.primerplayer.TestcontainersConfiguration;
import cl.primerplayer.TokensDePrueba;
import cl.primerplayer.TokensDePrueba.UsuarioConToken;
import cl.primerplayer.inventario.api.AjustarStockUseCase;
import cl.primerplayer.inventario.api.AjustarStockUseCase.ReemplazoStock;
import cl.primerplayer.shared.api.Rol;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ImportarProductosIntegracionTest {

    private static final String ENDPOINT = "/api/v1/admin/productos/importar";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired AjustarStockUseCase ajustarStock;
    @Autowired TransactionTemplate tx;

    UsuarioConToken admin, vendedor, cliente;

    @BeforeEach
    void usuarioAdmin() {
        admin = TokensDePrueba.crear(jdbc, encoder, Rol.ADMIN);
        vendedor = TokensDePrueba.crear(jdbc, encoder, Rol.VENDEDOR);
        cliente = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
    }

    @Test
    void importarJson_creaProductoYRegistraAjusteConMotivo() throws Exception {
        String nombre = "Producto importado " + UUID.randomUUID();
        String body = """
                {"filas":[{"nombre":"%s","precio":4990,"stock":7,"categoria":"tcg","publicado":true}]}
                """.formatted(nombre);

        mvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creados").value(1))
                .andExpect(jsonPath("$.actualizados").value(0))
                .andExpect(jsonPath("$.errores").isEmpty());

        Long productoId = jdbc.queryForObject("SELECT id FROM producto WHERE nombre = ?", Long.class, nombre);
        assertThat(jdbc.queryForObject("SELECT stock FROM producto WHERE id = ?", Integer.class, productoId)).isEqualTo(7);
        assertThat(jdbc.queryForObject("""
                SELECT motivo FROM movimiento_stock WHERE producto_id = ?
                """, String.class, productoId)).isEqualTo("Importación Excel");
        assertThat(jdbc.queryForObject("""
                SELECT usuario_id FROM movimiento_stock WHERE producto_id = ?
                """, Long.class, productoId)).isEqualTo(admin.id());
    }

    @Test
    void importarJson_soloAdmin() throws Exception {
        String body = """
                {"filas":[{"nombre":"No importar","precio":1000,"stock":1,"categoria":"tcg"}]}
                """;
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, cliente.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, vendedor.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void importarArchivoCsv_creaProductoYDevuelveErroresPorFila() throws Exception {
        String nombre = "CSV importado " + UUID.randomUUID();
        String csv = """
                Nombre,Precio,Stock,Categoria,Publicado
                %s,2490,4,tcg,Sí
                ,1000,1,tcg,No
                """.formatted(nombre);
        var archivo = new MockMultipartFile("archivo", "stock.csv", "text/csv",
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mvc.perform(multipart(ENDPOINT).file(archivo).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creados").value(1))
                .andExpect(jsonPath("$.errores[0].fila").value(2))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El nombre es requerido"));

        assertThat(jdbc.queryForObject("SELECT stock FROM producto WHERE nombre = ?", Integer.class, nombre)).isEqualTo(4);
    }

    @Test
    void importarJson_actualizaPorNombreConservandoOpcionalesVaciosYContinuaConErrores() throws Exception {
        String nombre = "Producto existente " + UUID.randomUUID();
        String crear = """
                {"filas":[{"nombre":"%s","precio":1000,"stock":2,"categoria":"tcg","marca":"Marca A","publicado":true}]}
                """.formatted(nombre);
        mvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(crear))
                .andExpect(status().isCreated());

        String body = """
                {"filas":[
                  {"nombre":"%s","precio":1500,"stock":10,"publicado":true},
                  {"nombre":"","precio":1000,"stock":0,"categoria":"tcg"},
                  {"nombre":"Producto negativo","precio":-1,"stock":0,"categoria":"tcg"}
                ]}
                """.formatted(nombre);
        mvc.perform(post(ENDPOINT).header(HttpHeaders.AUTHORIZATION, admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creados").value(0))
                .andExpect(jsonPath("$.actualizados").value(1))
                .andExpect(jsonPath("$.errores.length()").value(2))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El nombre es requerido"))
                .andExpect(jsonPath("$.errores[1].mensaje").value("El precio no puede ser negativo"));

        assertThat(jdbc.queryForObject("SELECT stock FROM producto WHERE nombre = ?", Integer.class, nombre)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT marca FROM producto WHERE nombre = ?", String.class, nombre)).isEqualTo("Marca A");
    }

    @Test
    void reemplazarStockConcurrente_serializaCambiosYRegistraKardexConsistente() throws Exception {
        long producto = jdbc.queryForObject("""
                INSERT INTO producto (nombre, precio, categoria_id, stock)
                VALUES (?, 1000, (SELECT id FROM categoria WHERE slug = 'tcg'), 5)
                RETURNING id
                """, Long.class, "Stock concurrente " + UUID.randomUUID());
        var inicio = new CountDownLatch(1);
        var completados = new AtomicInteger();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            pool.submit(() -> {
                inicio.await();
                tx.executeWithoutResult(s -> ajustarStock.reemplazar(
                        new ReemplazoStock(producto, 8, "Importación Excel", admin.id())));
                completados.incrementAndGet();
                return null;
            });
            pool.submit(() -> {
                inicio.await();
                tx.executeWithoutResult(s -> ajustarStock.reemplazar(
                        new ReemplazoStock(producto, 13, "Importación Excel", admin.id())));
                completados.incrementAndGet();
                return null;
            });
            inicio.countDown();
        }

        assertThat(completados).hasValue(2);
        int finalStock = jdbc.queryForObject("SELECT stock FROM producto WHERE id = ?", Integer.class, producto);
        assertThat(finalStock).isIn(8, 13);
        assertThat(jdbc.queryForObject("""
                SELECT coalesce(sum(cantidad), 0) FROM movimiento_stock WHERE producto_id = ?
                """, Integer.class, producto)).isEqualTo(finalStock - 5);
    }
}
