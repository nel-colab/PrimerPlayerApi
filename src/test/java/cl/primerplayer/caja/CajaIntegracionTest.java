package cl.primerplayer.caja;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
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

import cl.primerplayer.TestcontainersConfiguration;
import cl.primerplayer.TokensDePrueba;
import cl.primerplayer.TokensDePrueba.UsuarioConToken;
import cl.primerplayer.shared.api.Rol;

/**
 * Cada prueba trabaja sobre un día propio (lejano y distinto) para no mezclarse con otras pruebas que crean ventas hoy.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CajaIntegracionTest {

    static final ZoneId CHILE = ZoneId.of("America/Santiago");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;

    UsuarioConToken vendedor, cliente;
    org.springframework.transaction.support.TransactionTemplate tx;

    @Autowired
    void transacciones(org.springframework.transaction.PlatformTransactionManager txManager) {
        tx = new org.springframework.transaction.support.TransactionTemplate(txManager);
    }

    @BeforeEach
    void usuarios() {
        vendedor = TokensDePrueba.crear(jdbc, encoder, Rol.VENDEDOR);
        cliente = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
    }

    /** Un día único en el pasado para esta prueba. */
    static LocalDate diaUnico() {
        return LocalDate.of(2020, 1, 1).plusDays(Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1500));
    }

    long pedido(LocalDate dia, int hora, String canal, String estado, long total) {
        var ts = dia.atTime(hora, 0).atZone(CHILE).toOffsetDateTime();
        return jdbc.queryForObject("""
                INSERT INTO pedido (codigo, cliente_id, vendedor_id, canal, estado, total, pagado_en, creado_en, actualizado_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, "TC" + UUID.randomUUID().toString().substring(0, 10), cliente.id(),
                "tienda".equals(canal) ? vendedor.id() : null, canal, estado, total, ts, ts, ts);
    }

    void item(long pedidoId, String nombre, int cantidad, long precio) {
        long productoId = jdbc.queryForObject("""
                INSERT INTO producto (nombre, precio, categoria_id) VALUES (?, ?, (SELECT id FROM categoria WHERE slug = 'tcg-pokemon'))
                RETURNING id""", Long.class, nombre, precio);
        jdbc.update("INSERT INTO pedido_item (pedido_id, tipo, producto_id, nombre, cantidad, precio_unitario) VALUES (?, 'product', ?, ?, ?, ?)",
                pedidoId, productoId, nombre, cantidad, precio);
    }

    void efectivo(long pedidoId, long monto, long recibido) {
        jdbc.update("INSERT INTO pago (pedido_id, metodo, monto, efectivo_recibido, vuelto_entregado) VALUES (?, 'efectivo', ?, ?, ?)",
                pedidoId, monto, recibido, recibido - monto);
    }

    void pago(long pedidoId, String metodo, long monto) {
        jdbc.update("INSERT INTO pago (pedido_id, metodo, monto, numero_boleta) VALUES (?, ?, ?, ?)",
                pedidoId, metodo, monto, "debito".equals(metodo) ? "B-1" : null);
    }

    /**
     * Día con: venta mostrador mixta (efectivo 10.000 + débito 5.000), venta web (webpay 7.000) y una anulada.
     * En una transacción: el trigger diferido exige Σ pagos = total al confirmar, no en cada INSERT.
     */
    LocalDate diaConVentas() {
        LocalDate dia = diaUnico();
        tx.executeWithoutResult(s -> {
            long mostrador = pedido(dia, 12, "tienda", "ENTREGADO", 15000);
            item(mostrador, "Sobre A", 3, 5000);
            efectivo(mostrador, 10000, 20000);
            pago(mostrador, "debito", 5000);
            long web = pedido(dia, 20, "web", "PAGADO", 7000);
            item(web, "Sobre B", 1, 7000);
            pago(web, "webpay", 7000);
            long anulada = pedido(dia, 15, "tienda", "ANULADO", 9000);
            item(anulada, "Sobre C", 1, 9000);
            efectivo(anulada, 9000, 9000);
            pedido(dia.plusDays(1), 0, "tienda", "ENTREGADO", 0);   // 00:00 del día siguiente: fuera del día
        });
        return dia;
    }

    ResultActions como(UsuarioConToken u, MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(u == null ? req : req.header(HttpHeaders.AUTHORIZATION, u.bearer()));
    }

    ResultActions cerrar(LocalDate dia, long fondo, long contado, String obs) throws Exception {
        return como(vendedor, put("/api/v1/admin/caja/" + dia + "/cierre").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fondoInicial\":%d,\"efectivoContado\":%d,\"observaciones\":\"%s\"}".formatted(fondo, contado, obs)));
    }

    @Test
    void resumen_calculaDesdeLasVentasValidasDelDiaDeChile() throws Exception {
        LocalDate dia = diaConVentas();
        como(vendedor, get("/api/v1/admin/caja/" + dia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sales").value(2))                 // la anulada no cuenta
                .andExpect(jsonPath("$.units").value(4))
                .andExpect(jsonPath("$.total").value(22000))
                .andExpect(jsonPath("$.storeTotal").value(15000))
                .andExpect(jsonPath("$.webTotal").value(7000))
                .andExpect(jsonPath("$.byMethod.efectivo").value(10000))   // pago mixto: cada método aporta su parte
                .andExpect(jsonPath("$.byMethod.debito").value(5000))
                .andExpect(jsonPath("$.byMethod.webpay").value(7000))
                .andExpect(jsonPath("$.cashPayments").value(1))
                .andExpect(jsonPath("$.cashNet").value(10000))            // recibido 20.000 − vuelto 10.000
                .andExpect(jsonPath("$.items[0].name").value("Sobre A"))
                .andExpect(jsonPath("$.items[0].units").value(3))
                .andExpect(jsonPath("$.closure").doesNotExist());
    }

    @Test
    void cierre_calculaEnElServidorYVolverACerrarGuardaHistorial() throws Exception {
        LocalDate dia = diaConVentas();

        cerrar(dia, 50000, 58000, "Primer conteo")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingCash").value(50000))
                .andExpect(jsonPath("$.cashNet").value(10000))
                .andExpect(jsonPath("$.expectedCash").value(60000))
                .andExpect(jsonPath("$.difference").value(-2000))
                .andExpect(jsonPath("$.status").value("faltante"))
                .andExpect(jsonPath("$.orderCount").value(2))
                .andExpect(jsonPath("$.totalSales").value(22000))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.closedBy").value("Prueba VENDEDOR"));

        cerrar(dia, 50000, 60000, "Recuento")
                .andExpect(jsonPath("$.difference").value(0))
                .andExpect(jsonPath("$.status").value("cuadrada"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.notes").value("Recuento"));
        assertThat(jdbc.queryForObject("SELECT datos->>'observaciones' FROM cierre_caja_version WHERE fecha = ? AND version = 1",
                String.class, dia)).isEqualTo("Primer conteo");

        como(vendedor, get("/api/v1/admin/caja/" + dia)).andExpect(jsonPath("$.closure.version").value(2));
        como(vendedor, get("/api/v1/admin/caja/cierres").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.date == '" + dia + "')].status").value("cuadrada"));
    }

    @Test
    void cierre_validaciones() throws Exception {
        LocalDate manana = LocalDate.now(CHILE).plusDays(1);
        cerrar(manana, 0, 0, "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
        cerrar(diaUnico(), -1, 0, "").andExpect(status().isBadRequest());
        como(vendedor, get("/api/v1/admin/caja/no-es-fecha")).andExpect(status().isBadRequest());
    }

    @Test
    void cierre_diaSinVentas_cuadraConElFondo() throws Exception {
        LocalDate dia = diaUnico();
        cerrar(dia, 30000, 30000, "Día sin ventas")
                .andExpect(jsonPath("$.orderCount").value(0))
                .andExpect(jsonPath("$.expectedCash").value(30000))
                .andExpect(jsonPath("$.status").value("cuadrada"));
    }

    @Test
    void seguridad() throws Exception {
        LocalDate dia = diaUnico();
        como(null, get("/api/v1/admin/caja/" + dia)).andExpect(status().isUnauthorized());
        como(cliente, get("/api/v1/admin/caja/" + dia)).andExpect(status().isForbidden());
        como(cliente, get("/api/v1/admin/caja/cierres")).andExpect(status().isForbidden());
        como(TokensDePrueba.crear(jdbc, encoder, Rol.ADMIN), get("/api/v1/admin/caja/" + dia)).andExpect(status().isOk());
    }
}
