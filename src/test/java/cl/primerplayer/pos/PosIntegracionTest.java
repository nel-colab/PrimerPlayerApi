package cl.primerplayer.pos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class PosIntegracionTest {

    static final String VENTAS = "/api/v1/admin/pos/ventas";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;

    UsuarioConToken vendedor, admin, cliente;

    @BeforeEach
    void usuarios() {
        vendedor = TokensDePrueba.crear(jdbc, encoder, Rol.VENDEDOR);
        admin = TokensDePrueba.crear(jdbc, encoder, Rol.ADMIN);
        cliente = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
    }

    // ---------- helpers ----------

    record Prod(long id, String codigo) {}

    Prod producto(long precio, int stock, boolean publicado, Integer limite) {
        String ean = TokensDePrueba.ean13();
        long id = jdbc.queryForObject("""
                INSERT INTO producto (nombre, precio, categoria_id, stock, publicado, codigo_barras, limite_por_cliente)
                VALUES (?, ?, (SELECT id FROM categoria WHERE slug = 'tcg'), ?, ?, ?, ?) RETURNING id
                """, Long.class, "Sobre POS " + ean, precio, stock, publicado, ean, limite);
        return new Prod(id, ean);
    }

    long evento(long precio, int cupos) {
        return jdbc.queryForObject("""
                INSERT INTO evento (titulo, tipo, fecha_hora, lugar, precio, cupos_totales, descripcion, publicado)
                VALUES ('Torneo POS', 'torneo-tcg', now() + interval '7 days', 'Tienda', ?, ?, 'x', true) RETURNING id
                """, Long.class, precio, cupos);
    }

    int stock(long id) {
        return jdbc.queryForObject("SELECT stock FROM producto WHERE id = ?", Integer.class, id);
    }

    static String venta(String lineas, String pagos, Boolean confirmar) {
        return "{\"lineas\":[" + lineas + "],\"pagos\":[" + pagos + "]"
                + (confirmar == null ? "" : ",\"confirmarVueltoDistinto\":" + confirmar) + "}";
    }

    static String linea(String tipo, long id, int cantidad) {
        return "{\"type\":\"%s\",\"itemId\":%d,\"quantity\":%d}".formatted(tipo, id, cantidad);
    }

    static String efectivo(long monto, long recibido, Long vuelto) {
        return "{\"method\":\"efectivo\",\"amount\":%d,\"details\":{\"cashReceived\":%d%s}}"
                .formatted(monto, recibido, vuelto == null ? "" : ",\"changeGiven\":" + vuelto);
    }

    static String conBoleta(String metodo, long monto, String boleta) {
        return "{\"method\":\"%s\",\"amount\":%d,\"details\":{\"receiptNumber\":%s}}"
                .formatted(metodo, monto, boleta == null ? "null" : "\"" + boleta + "\"");
    }

    ResultActions como(UsuarioConToken u, MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(u == null ? req : req.header(HttpHeaders.AUTHORIZATION, u.bearer()));
    }

    ResultActions vender(UsuarioConToken u, String body, String clave) throws Exception {
        var req = post(VENTAS).contentType(MediaType.APPLICATION_JSON).content(body);
        if (clave != null) {
            req = req.header("Idempotency-Key", clave);
        }
        return como(u, req);
    }

    static String codigo(ResultActions r) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.code");
    }

    // ---------- lectura por código de barras (pistola = teclado + Enter) ----------

    @Test
    void codigoDeBarras_getComunPorCodigo() throws Exception {
        Prod p = producto(4990, 7, true, null);
        Prod oculto = producto(4990, 7, false, null);
        Prod eliminado = producto(4990, 7, true, null);
        jdbc.update("UPDATE producto SET eliminado_en = now() WHERE id = ?", eliminado.id());

        como(vendedor, get("/api/v1/admin/pos/productos/codigo/" + p.codigo()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) p.id()))
                .andExpect(jsonPath("$.codigoBarras").value(p.codigo()))
                .andExpect(jsonPath("$.precio").value(4990))
                .andExpect(jsonPath("$.stock").value(7));
        for (String c : new String[] {oculto.codigo(), eliminado.codigo(), "0000000000000"}) {
            como(vendedor, get("/api/v1/admin/pos/productos/codigo/" + c))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("item-unavailable"));
        }
        como(null, get("/api/v1/admin/pos/productos/codigo/" + p.codigo())).andExpect(status().isUnauthorized());
        como(cliente, get("/api/v1/admin/pos/productos/codigo/" + p.codigo())).andExpect(status().isForbidden());
    }

    // ---------- venta ----------

    @Test
    void venta_efectivoConVuelto_entregadaYConPagosEnElPedido() throws Exception {
        Prod p = producto(4990, 5, true, null);
        var r = vender(vendedor, venta(linea("product", p.id(), 2), efectivo(9980, 10000, null), null),
                UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ENTREGADO"))
                .andExpect(jsonPath("$.channel").value("tienda"))
                .andExpect(jsonPath("$.total").value(9980))
                .andExpect(jsonPath("$.customerName").value("Venta en mostrador"))
                .andExpect(jsonPath("$.seller").value("Prueba VENDEDOR"))
                .andExpect(jsonPath("$.payments", hasSize(1)))
                .andExpect(jsonPath("$.payments[0].method").value("efectivo"))
                .andExpect(jsonPath("$.payments[0].amount").value(9980))
                .andExpect(jsonPath("$.payments[0].details.cashReceived").value(10000))
                .andExpect(jsonPath("$.payments[0].details.changeGiven").value(20));
        String codigo = codigo(r);
        assertThat(stock(p.id())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT tipo FROM movimiento_stock WHERE referencia = ?", String.class, codigo))
                .isEqualTo("VENTA_MOSTRADOR");

        // El panel de pedidos también muestra los pagos
        como(vendedor, get("/api/v1/admin/pedidos").param("codigo", codigo))
                .andExpect(jsonPath("$.content[0].payments[0].method").value("efectivo"));
    }

    @Test
    void venta_pagoMixtoConCartaYSaldoAFavor() throws Exception {
        Prod p = producto(60000, 2, true, null);
        String pagos = String.join(",", efectivo(10000, 10000, 0L), conBoleta("debito", 30000, "B-555"),
                "{\"method\":\"cartas-tcg\",\"amount\":20000,\"details\":{\"tcgCard\":{\"name\":\"Charizard ex\",\"code\":\"SV3-199\",\"value\":25000}}}");
        vender(vendedor, venta(linea("product", p.id(), 1), pagos, null), UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payments", hasSize(3)))
                .andExpect(jsonPath("$.payments[1].details.receiptNumber").value("B-555"))
                .andExpect(jsonPath("$.payments[2].details.tcgCard.name").value("Charizard ex"))
                .andExpect(jsonPath("$.payments[2].details.creditBalance").value(5000));
    }

    @Test
    void venta_erroresDePago_noDescuentanStock() throws Exception {
        Prod p = producto(4990, 5, true, null);
        String l = linea("product", p.id(), 1);

        vender(vendedor, venta(l, conBoleta("debito", 3000, "B-1"), null), UUID.randomUUID().toString())
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("payments-mismatch"));
        vender(vendedor, venta(l, conBoleta("debito", 4990, null), null), UUID.randomUUID().toString())
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("receipt-required"))
                .andExpect(jsonPath("$.errors[0].line").value(0));
        vender(vendedor, venta(l, "{\"method\":\"transferencia\",\"amount\":4990}", null), UUID.randomUUID().toString())
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("transfer-proof-required"));
        vender(vendedor, venta(l, "{\"method\":\"transferencia\",\"amount\":4990,\"details\":{\"transferProofId\":\""
                        + UUID.randomUUID() + "\"}}", null), UUID.randomUUID().toString())
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("transfer-proof-required"));
        vender(vendedor, venta(l, "{\"method\":\"cartas-tcg\",\"amount\":4990,\"details\":{\"tcgCard\":{\"name\":\"X\"}}}", null),
                UUID.randomUUID().toString())
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("tcg-card-required"));
        vender(vendedor, venta(l, conBoleta("tarjeta-credito", 4990, "B"), null), UUID.randomUUID().toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));

        assertThat(stock(p.id())).isEqualTo(5);   // nada quedó a medias
    }

    @Test
    void venta_vueltoDistinto_advierteYSeAceptaConfirmando() throws Exception {
        Prod p = producto(4990, 5, true, null);
        String body = venta(linea("product", p.id(), 1), efectivo(4990, 10000, 5000L), null);
        vender(vendedor, body, UUID.randomUUID().toString())
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("cash-change-mismatch"));
        vender(vendedor, venta(linea("product", p.id(), 1), efectivo(4990, 10000, 5000L), true), UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payments[0].details.changeGiven").value(5000));
    }

    @Test
    void venta_soloCuposDeEvento_quedaPagadaEInscripcionConfirmada() throws Exception {
        long e = evento(7000, 10);
        String codigo = codigo(vender(vendedor, venta(linea("event", e, 2), conBoleta("debito", 14000, "B-9"), null),
                UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAGADO")));
        assertThat(jdbc.queryForObject("SELECT cupos_ocupados FROM evento WHERE id = ?", Integer.class, e)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT i.estado FROM inscripcion i JOIN pedido p ON p.id = i.pedido_id WHERE p.codigo = ?
                """, String.class, codigo)).isEqualTo("CONFIRMADA");
    }

    @Test
    void venta_mostradorNoAcumulaLimitePorCliente() throws Exception {
        Prod limitado = producto(1000, 10, true, 1);
        vender(vendedor, venta(linea("product", limitado.id(), 3), efectivo(3000, 3000, null), null),
                UUID.randomUUID().toString()).andExpect(status().isCreated());
    }

    @Test
    void venta_idempotencia() throws Exception {
        Prod p = producto(4990, 5, true, null);
        String clave = UUID.randomUUID().toString();
        String body = venta(linea("product", p.id(), 1), efectivo(4990, 5000, null), null);
        String primero = codigo(vender(vendedor, body, clave).andExpect(status().isCreated()));

        vender(vendedor, body, clave).andExpect(status().isCreated()).andExpect(jsonPath("$.code").value(primero))
                .andExpect(jsonPath("$.payments", hasSize(1)));
        assertThat(stock(p.id())).isEqualTo(4);

        vender(vendedor, venta(linea("product", p.id(), 1), conBoleta("debito", 4990, "B"), null), clave)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("idempotency-conflict"));
        vender(vendedor, body, null).andExpect(status().isBadRequest());
    }

    @Test
    void validacion_previaAlCobro() throws Exception {
        Prod p = producto(4990, 1, true, null);
        como(vendedor, post("/api/v1/admin/pos/validacion").contentType(MediaType.APPLICATION_JSON)
                        .content(venta(linea("product", p.id(), 2), "", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.lines[0].available").value(1))
                .andExpect(jsonPath("$.errors[0].code").value("stock-limit"));
    }

    // ---------- anulación ----------

    @Test
    void anulacion_soloAdminReponeStockYNoSeRepite() throws Exception {
        Prod p = producto(4990, 5, true, null);
        String codigo = codigo(vender(vendedor, venta(linea("product", p.id(), 2), efectivo(9980, 9980, null), null),
                UUID.randomUUID().toString()));
        assertThat(stock(p.id())).isEqualTo(3);

        como(vendedor, post(VENTAS + "/" + codigo + "/anulacion")).andExpect(status().isForbidden());
        como(admin, post(VENTAS + "/" + codigo + "/anulacion"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ANULADO"))
                .andExpect(jsonPath("$.payments", hasSize(1)));   // el pago queda registrado
        assertThat(stock(p.id())).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimiento_stock WHERE referencia = ? AND tipo = 'ANULACION'",
                Integer.class, codigo)).isEqualTo(1);

        como(admin, post(VENTAS + "/" + codigo + "/anulacion"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("order-invalid-state"));
        como(admin, post(VENTAS + "/PP-NO-EXISTE/anulacion")).andExpect(status().isNotFound());
    }

    @Test
    void anulacion_deUnPedidoWeb_noPermitida() throws Exception {
        String codigo = "TW" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO pedido (codigo, cliente_id, canal, estado, total) VALUES (?, ?, 'web', 'PAGADO', 0)",
                codigo, cliente.id());
        como(admin, post(VENTAS + "/" + codigo + "/anulacion"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("order-invalid-state"));
    }

    // ---------- pagos webpay dentro del pedido ----------

    @Test
    void pedidoWeb_muestraElPagoWebpayConSusDatosDeTarjeta() throws Exception {
        String codigo = "TW" + UUID.randomUUID().toString().substring(0, 8);
        Long id = jdbc.queryForObject("""
                INSERT INTO pedido (codigo, cliente_id, canal, estado, total, pagado_en)
                VALUES (?, ?, 'web', 'PAGADO', 4990, now()) RETURNING id
                """, Long.class, codigo, cliente.id());
        jdbc.update("""
                INSERT INTO transaccion_webpay (pedido_id, buy_order, session_id, token, monto, estado, authorization_code,
                                                payment_type_code, installments_number, card_last4)
                VALUES (?, ?, 's', ?, 4990, 'AUTORIZADA', '123456', 'VD', 0, '6623')
                """, id, codigo, "tok-" + codigo);
        jdbc.update("INSERT INTO pago (pedido_id, metodo, monto) VALUES (?, 'webpay', 4990)", id);

        como(cliente, get("/api/v1/me/pedidos/" + codigo))
                .andExpect(jsonPath("$.payments[0].method").value("webpay"))
                .andExpect(jsonPath("$.payments[0].amount").value(4990))
                .andExpect(jsonPath("$.payments[0].details.authorizationCode").value("123456"))
                .andExpect(jsonPath("$.payments[0].details.cardLast4").value("6623"))
                .andExpect(jsonPath("$.payments[0].details.paymentType").value("VD"));
    }

    // ---------- seguridad ----------

    @Test
    void seguridad() throws Exception {
        Prod p = producto(1000, 5, true, null);
        String body = venta(linea("product", p.id(), 1), efectivo(1000, 1000, null), null);
        vender(null, body, UUID.randomUUID().toString()).andExpect(status().isUnauthorized());
        vender(cliente, body, UUID.randomUUID().toString()).andExpect(status().isForbidden());
        vender(admin, body, UUID.randomUUID().toString()).andExpect(status().isCreated());
        como(cliente, post("/api/v1/admin/pos/validacion").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }
}
