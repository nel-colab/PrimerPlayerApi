package cl.primerplayer.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import cl.primerplayer.TestcontainersConfiguration;
import cl.primerplayer.TokensDePrueba;
import cl.primerplayer.TokensDePrueba.UsuarioConToken;
import cl.primerplayer.inventario.api.InventarioOperaciones;
import cl.primerplayer.inventario.api.InventarioOperaciones.LineaStock;
import cl.primerplayer.inventario.api.StockInsuficienteException;
import cl.primerplayer.inventario.api.TipoMovimiento;
import cl.primerplayer.inventario.events.StockRepuesto;
import cl.primerplayer.shared.api.Rol;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@RecordApplicationEvents
class InventarioIntegracionTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired InventarioOperaciones inventario;
    @Autowired TransactionTemplate tx;
    @Autowired ApplicationEvents eventos;

    UsuarioConToken admin, vendedor, cliente;

    @BeforeEach
    void usuarios() {
        admin = TokensDePrueba.crear(jdbc, encoder, Rol.ADMIN);
        vendedor = TokensDePrueba.crear(jdbc, encoder, Rol.VENDEDOR);
        cliente = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
    }

    private long producto(int stock) {
        return jdbc.queryForObject("""
                INSERT INTO producto (nombre, precio, categoria_id, stock, publicado)
                VALUES ('Prueba inventario', 1000, (SELECT id FROM categoria WHERE slug = 'tcg-pokemon'), ?, TRUE)
                RETURNING id""", Long.class, stock);
    }

    private int stock(long id) {
        return jdbc.queryForObject("SELECT stock FROM producto WHERE id = ?", Integer.class, id);
    }

    private ResultActions ajustar(UsuarioConToken u, long id, int delta, String motivo) throws Exception {
        var req = post("/api/v1/admin/inventario/" + id + "/ajustes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"delta\":%d,\"motivo\":\"%s\"}".formatted(delta, motivo));
        return mvc.perform(u == null ? req : req.header(HttpHeaders.AUTHORIZATION, u.bearer()));
    }

    // ---------- ajustes ----------

    @Test
    void ajuste_sumaYRestaDejaKardexConUsuarioYMotivo() throws Exception {
        long id = producto(0);
        ajustar(vendedor, id, 10, "Ingreso factura 123").andExpect(status().isOk())
                .andExpect(jsonPath("$.productoId").value((int) id)).andExpect(jsonPath("$.stock").value(10));
        ajustar(admin, id, -3, "Merma").andExpect(status().isOk()).andExpect(jsonPath("$.stock").value(7));

        mvc.perform(get("/api/v1/admin/inventario/" + id + "/movimientos").header(HttpHeaders.AUTHORIZATION, vendedor.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].cantidad").value(-3))
                .andExpect(jsonPath("$.content[0].stockResultante").value(7))
                .andExpect(jsonPath("$.content[0].motivo").value("Merma"))
                .andExpect(jsonPath("$.content[0].usuarioId").value((int) admin.id()))
                .andExpect(jsonPath("$.content[1].tipo").value("AJUSTE"))
                .andExpect(jsonPath("$.content[1].cantidad").value(10));
    }

    @Test
    void ajuste_quedariaNegativo_409StockLimitYNoCambiaNada() throws Exception {
        long id = producto(2);
        ajustar(vendedor, id, -5, "Error").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("stock-limit"))
                .andExpect(jsonPath("$.errors[0].itemId").value((int) id));
        assertThat(stock(id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimiento_stock WHERE producto_id = ?", Integer.class, id)).isZero();

        long agotado = producto(0);
        ajustar(vendedor, agotado, -1, "Error").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("out-of-stock"));
    }

    @Test
    void ajuste_productoEliminadoOInexistente_404() throws Exception {
        long id = producto(5);
        jdbc.update("UPDATE producto SET eliminado_en = now() WHERE id = ?", id);
        ajustar(vendedor, id, 1, "x").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("item-unavailable"));
        ajustar(vendedor, 999_999_999L, 1, "x").andExpect(status().isNotFound());
    }

    @Test
    void ajuste_validaciones400() throws Exception {
        long id = producto(5);
        ajustar(vendedor, id, 0, "x").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("delta"));
        ajustar(vendedor, id, 1, " ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("motivo"));
        ajustar(vendedor, id, 10_000, "x").andExpect(status().isBadRequest());
    }

    @Test
    void ajuste_deCeroAPositivo_publicaStockRepuesto() throws Exception {
        long id = producto(0);
        ajustar(vendedor, id, 4, "Reposición").andExpect(status().isOk());
        ajustar(vendedor, id, 1, "Más").andExpect(status().isOk());   // 4 -> 5: no es reposición
        assertThat(eventos.stream(StockRepuesto.class).filter(e -> e.productoId() == id))
                .containsExactly(new StockRepuesto(id, 4));
    }

    // ---------- operaciones para checkout / pos ----------

    @Test
    void descontar_exigeTransaccionAbierta() {
        long id = producto(5);
        assertThatThrownBy(() -> inventario.descontar(List.of(new LineaStock(id, 1)), TipoMovimiento.VENTA_WEB, "PP-T"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void descontar_variasLineas_todoONada() {
        long a = producto(5), b = producto(1);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> inventario.descontar(
                List.of(new LineaStock(a, 2), new LineaStock(b, 3)), TipoMovimiento.VENTA_WEB, "PP-T1")))
                .isInstanceOfSatisfying(StockInsuficienteException.class, e -> {
                    assertThat(e.code()).isEqualTo("stock-limit");
                    assertThat(e.errores().getFirst().line()).isEqualTo(1);   // índice original de la línea
                });
        assertThat(stock(a)).isEqualTo(5);   // rollback de la primera línea
        assertThat(stock(b)).isEqualTo(1);

        tx.executeWithoutResult(s -> inventario.descontar(
                List.of(new LineaStock(b, 1), new LineaStock(a, 2)), TipoMovimiento.VENTA_MOSTRADOR, "PP-T2"));
        assertThat(stock(a)).isEqualTo(3);
        assertThat(stock(b)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimiento_stock WHERE referencia = 'PP-T2' AND cantidad < 0",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void reponer_devuelveStockAunqueElProductoEsteEliminado() {
        long id = producto(0);
        jdbc.update("UPDATE producto SET eliminado_en = now() WHERE id = ?", id);
        tx.executeWithoutResult(s -> inventario.reponer(List.of(new LineaStock(id, 2)), TipoMovimiento.ANULACION, "PP-T3"));
        assertThat(stock(id)).isEqualTo(2);
    }

    @Test
    void concurrencia_cincuentaHilosCompranDiezUnidades_soloDiezGananYNuncaNegativo() throws Exception {
        long id = producto(10);
        int hilos = 50;
        var largada = new CountDownLatch(1);
        var exitos = new AtomicInteger();
        var sinStock = new AtomicInteger();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < hilos; i++) {
                pool.submit(() -> {
                    largada.await();
                    try {
                        tx.executeWithoutResult(s -> inventario.descontar(
                                List.of(new LineaStock(id, 1)), TipoMovimiento.VENTA_WEB, "PP-CONC"));
                        exitos.incrementAndGet();
                    } catch (StockInsuficienteException esperado) {
                        sinStock.incrementAndGet();
                    }
                    return null;
                });
            }
            largada.countDown();
        }
        assertThat(exitos.get()).isEqualTo(10);
        assertThat(sinStock.get()).isEqualTo(40);
        assertThat(stock(id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimiento_stock WHERE producto_id = ?", Integer.class, id))
                .isEqualTo(10);
    }

    // ---------- configuración y seguridad ----------

    @Test
    void configuracion_soloAdmin_yCambiaElUmbralQueUsaCatalogo() throws Exception {
        var url = "/api/v1/admin/inventario/configuracion";
        mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, vendedor.bearer())).andExpect(status().isForbidden());
        mvc.perform(put(url).header(HttpHeaders.AUTHORIZATION, vendedor.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"umbralStockBajo\":5}")).andExpect(status().isForbidden());
        mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, cliente.bearer())).andExpect(status().isForbidden());
        mvc.perform(get(url)).andExpect(status().isUnauthorized());

        try {
            mvc.perform(put(url).header(HttpHeaders.AUTHORIZATION, admin.bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"umbralStockBajo\":7}")).andExpect(status().isOk()).andExpect(jsonPath("$.umbralStockBajo").value(7));
            mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, admin.bearer())).andExpect(jsonPath("$.umbralStockBajo").value(7));

            long id = producto(6);   // 6 <= 7 => stock bajo según el umbral nuevo
            mvc.perform(get("/api/v1/admin/productos").param("q", String.valueOf(id)).param("estado", "stock-bajo")
                    .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                    .andExpect(jsonPath("$.content[*].id", org.hamcrest.Matchers.hasItem((int) id)));
        } finally {
            jdbc.update("UPDATE config_inventario SET umbral_stock_bajo = 3");
        }
    }

    @Test
    void ajustesYKardex_anonimo401_cliente403() throws Exception {
        long id = producto(1);
        ajustar(null, id, 1, "x").andExpect(status().isUnauthorized());
        ajustar(cliente, id, 1, "x").andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/inventario/" + id + "/movimientos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/inventario/" + id + "/movimientos").header(HttpHeaders.AUTHORIZATION, cliente.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/inventario/" + id + "/movimientos").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk());
    }
}
