package cl.primerplayer.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

import com.jayway.jsonpath.JsonPath;

import cl.primerplayer.TestcontainersConfiguration;
import cl.primerplayer.TokensDePrueba;
import cl.primerplayer.TokensDePrueba.UsuarioConToken;
import cl.primerplayer.checkout.PasarelaWebpayFalsa.Resultado;
import cl.primerplayer.checkout.api.CheckoutUseCase;
import cl.primerplayer.shared.api.Rol;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PasarelaWebpayFalsa.Config.class})
@ActiveProfiles("test")
class CheckoutIntegracionTest {

    static final String RETORNO = "/api/v1/pagos/webpay/retorno";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired PasarelaWebpayFalsa webpay;
    @Autowired CheckoutUseCase checkout;

    UsuarioConToken cliente;

    @BeforeEach
    void preparar() {
        cliente = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
        webpay.creacionCaida = false;
    }

    // ---------- helpers ----------

    long producto(long precio, int stock, Integer limite) {
        return jdbc.queryForObject("""
                INSERT INTO producto (nombre, precio, categoria_id, stock, publicado, limite_por_cliente)
                VALUES (?, ?, (SELECT id FROM categoria WHERE slug = 'tcg-pokemon'), ?, true, ?) RETURNING id
                """, Long.class, "Sobre " + UUID.randomUUID().toString().substring(0, 6), precio, stock, limite);
    }

    long evento(long precio, int cupos, Integer limite) {
        return jdbc.queryForObject("""
                INSERT INTO evento (titulo, tipo, fecha_hora, lugar, precio, cupos_totales, limite_por_cliente,
                                    descripcion, publicado)
                VALUES ('Torneo ' || substr(md5(random()::text), 1, 6), 'torneo-tcg', now() + interval '20 days',
                        'Tienda', ?, ?, ?, 'x', true) RETURNING id
                """, Long.class, precio, cupos, limite);
    }

    static String carrito(Object... tipoIdCantidad) {
        List<String> lineas = new ArrayList<>();
        for (int i = 0; i < tipoIdCantidad.length; i += 3) {
            lineas.add("{\"type\":\"%s\",\"itemId\":%s,\"quantity\":%s}"
                    .formatted(tipoIdCantidad[i], tipoIdCantidad[i + 1], tipoIdCantidad[i + 2]));
        }
        return "{\"lineas\":[" + String.join(",", lineas) + "]}";
    }

    ResultActions comprar(UsuarioConToken u, String body, String clave) throws Exception {
        var req = post("/api/v1/checkout").contentType(MediaType.APPLICATION_JSON).content(body);
        if (clave != null) {
            req = req.header("Idempotency-Key", clave);
        }
        return mvc.perform(u == null ? req : req.header(HttpHeaders.AUTHORIZATION, u.bearer()));
    }

    String codigoDe(ResultActions r) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.order.code");
    }

    int stock(long productoId) {
        return jdbc.queryForObject("SELECT stock FROM producto WHERE id = ?", Integer.class, productoId);
    }

    int cuposOcupados(long eventoId) {
        return jdbc.queryForObject("SELECT cupos_ocupados FROM evento WHERE id = ?", Integer.class, eventoId);
    }

    String estado(String codigo) {
        return jdbc.queryForObject("SELECT estado FROM pedido WHERE codigo = ?", String.class, codigo);
    }

    String estadoTx(String codigo) {
        return jdbc.queryForObject("SELECT estado FROM transaccion_webpay WHERE buy_order = ?", String.class, codigo);
    }

    String estadoInscripcion(String codigo) {
        return jdbc.queryForObject("""
                SELECT i.estado FROM inscripcion i JOIN pedido p ON p.id = i.pedido_id WHERE p.codigo = ?
                """, String.class, codigo);
    }

    ResultActions retorno(String param, String valor) throws Exception {
        return mvc.perform(post(RETORNO).contentType(MediaType.APPLICATION_FORM_URLENCODED).param(param, valor));
    }

    // ---------- validación del carrito ----------

    @Test
    void validacion_publicaConErroresPorLinea() throws Exception {
        long ok = producto(4990, 5, null);
        long agotado = producto(2990, 0, null);

        mvc.perform(post("/api/v1/carrito/validacion").contentType(MediaType.APPLICATION_JSON)
                        .content(carrito("product", ok, 2, "product", agotado, 1, "product", 999_999_999L, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.total").value(9980 + 2990))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(4990))
                .andExpect(jsonPath("$.lines[0].available").value(5))
                .andExpect(jsonPath("$.lines[0].maxAllowed").value(nullValue()))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].line").value(1))
                .andExpect(jsonPath("$.errors[0].code").value("out-of-stock"))
                .andExpect(jsonPath("$.errors[1].line").value(2))
                .andExpect(jsonPath("$.errors[1].code").value("item-unavailable"));
    }

    @Test
    void validacion_conSesionEvaluaElLimitePorCliente() throws Exception {
        long limitado = producto(4990, 10, 2);
        mvc.perform(post("/api/v1/carrito/validacion").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, cliente.bearer()).content(carrito("product", limitado, 3)))
                .andExpect(jsonPath("$.lines[0].maxAllowed").value(2))
                .andExpect(jsonPath("$.errors[0].code").value("purchase-limit"))
                .andExpect(jsonPath("$.errors[0].maxAllowed").value(2));
    }

    // ---------- checkout ----------

    @Test
    void checkout_reservaStockYCuposEIniciaWebpay() throws Exception {
        long p = producto(4990, 5, null);
        long e = evento(7000, 10, null);

        var r = comprar(cliente, carrito("product", p, 2, "event", e, 1), UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith("/api/v1/me/pedidos/PP-")))
                .andExpect(jsonPath("$.order.status").value("PENDIENTE_PAGO"))
                .andExpect(jsonPath("$.order.total").value(16980))
                .andExpect(jsonPath("$.order.expiresAt").exists())
                .andExpect(jsonPath("$.payment.provider").value("webpay"))
                .andExpect(jsonPath("$.payment.url").value(PasarelaWebpayFalsa.URL))
                .andExpect(jsonPath("$.payment.token").exists());
        String codigo = codigoDe(r);

        assertThat(stock(p)).isEqualTo(3);
        assertThat(cuposOcupados(e)).isEqualTo(1);
        assertThat(estadoTx(codigo)).isEqualTo("CREADA");
        assertThat(estadoInscripcion(codigo)).isEqualTo("RESERVADA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimiento_stock WHERE referencia = ? AND tipo = 'VENTA_WEB'",
                Integer.class, codigo)).isEqualTo(1);
        mvc.perform(get("/api/v1/me/pedidos/" + codigo).header(HttpHeaders.AUTHORIZATION, cliente.bearer()))
                .andExpect(jsonPath("$.status").value("PENDIENTE_PAGO"));
    }

    @Test
    void checkout_idempotencia() throws Exception {
        long p = producto(4990, 5, null);
        String clave = UUID.randomUUID().toString();
        String primero = codigoDe(comprar(cliente, carrito("product", p, 1), clave).andExpect(status().isCreated()));

        // Mismo carrito y misma clave (doble clic, reintento de red): mismo pedido, mismo pago, stock descontado una vez
        comprar(cliente, carrito("product", p, 1), clave)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.code").value(primero))
                .andExpect(jsonPath("$.payment.token").value("tok-" + primero));
        assertThat(stock(p)).isEqualTo(4);

        comprar(cliente, carrito("product", p, 2), clave)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("idempotency-conflict"));
        comprar(cliente, carrito("product", p, 1), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
        comprar(cliente, carrito("product", p, 1), "no-es-uuid")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
    }

    @Test
    void checkout_correoSinVerificar_403() throws Exception {
        jdbc.update("UPDATE usuario SET email_verificado_en = NULL WHERE id = ?", cliente.id());
        long p = producto(4990, 5, null);
        comprar(cliente, carrito("product", p, 1), UUID.randomUUID().toString())
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("email-not-verified"));
        assertThat(stock(p)).isEqualTo(5);
    }

    @Test
    void checkout_limitePorClienteAcumulado() throws Exception {
        long p = producto(4990, 10, 2);
        String primero = codigoDe(comprar(cliente, carrito("product", p, 1), UUID.randomUUID().toString())
                .andExpect(status().isCreated()));

        // Ya tiene 1 (pedido pendiente cuenta); quiere 2 más con límite 2 → solo puede 1
        comprar(cliente, carrito("event", evento(1000, 5, null), 1, "product", p, 2), UUID.randomUUID().toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("purchase-limit"))
                .andExpect(jsonPath("$.errors[0].line").value(1))
                .andExpect(jsonPath("$.errors[0].maxAllowed").value(1));
        assertThat(stock(p)).isEqualTo(9);
        assertThat(primero).startsWith("PP-");
    }

    @Test
    void checkout_erroresDeCarrito() throws Exception {
        long agotado = producto(4990, 0, null);
        comprar(cliente, "{\"lineas\":[]}", UUID.randomUUID().toString())
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("empty"));
        comprar(cliente, carrito("product", producto(1000, 5, null), 0), UUID.randomUUID().toString())
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("invalid-quantity"));
        comprar(cliente, carrito("event", evento(1000, 5, null), 1, "product", agotado, 1), UUID.randomUUID().toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("out-of-stock"))
                .andExpect(jsonPath("$.errors[0].line").value(1));
        long lleno = evento(1000, 1, null);
        comprar(cliente, carrito("event", lleno, 2), UUID.randomUUID().toString())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("event-full"));
    }

    @Test
    void checkout_eventoGratuito_quedaPagadoSinWebpay() throws Exception {
        long gratis = evento(0, 30, null);
        var r = comprar(cliente, carrito("event", gratis, 2), UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.order.status").value("PAGADO"))
                .andExpect(jsonPath("$.payment").value(nullValue()));
        assertThat(estadoInscripcion(codigoDe(r))).isEqualTo("CONFIRMADA");
        assertThat(cuposOcupados(gratis)).isEqualTo(2);
    }

    @Test
    void checkout_webpayCaido_502YDevuelveElStock() throws Exception {
        long p = producto(4990, 5, null);
        webpay.creacionCaida = true;
        comprar(cliente, carrito("product", p, 2), UUID.randomUUID().toString())
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("payment-provider-error"));
        assertThat(stock(p)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT estado FROM pedido WHERE cliente_id = ? ORDER BY id DESC LIMIT 1",
                String.class, cliente.id())).isEqualTo("RECHAZADO");
    }

    // ---------- retorno desde Webpay ----------

    @Test
    void retorno_autorizado_pagaUnaSolaVez() throws Exception {
        long p = producto(4990, 5, null);
        long e = evento(7000, 10, null);
        String codigo = codigoDe(comprar(cliente, carrito("product", p, 1, "event", e, 1), UUID.randomUUID().toString()));

        retorno("token_ws", "tok-" + codigo)
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/pago/resultado?pedido=" + codigo)));
        assertThat(estado(codigo)).isEqualTo("PAGADO");
        assertThat(estadoTx(codigo)).isEqualTo("AUTORIZADA");
        assertThat(estadoInscripcion(codigo)).isEqualTo("CONFIRMADA");
        assertThat(jdbc.queryForObject("SELECT sum(monto) FROM pago p JOIN pedido x ON x.id = p.pedido_id WHERE x.codigo = ?",
                Long.class, codigo)).isEqualTo(11990L);
        assertThat(jdbc.queryForObject("SELECT card_last4 FROM transaccion_webpay WHERE buy_order = ?", String.class, codigo))
                .isEqualTo("6623");

        // Recarga o doble POST: no confirma de nuevo ni duplica el pago
        int commits = webpay.commits.get();
        mvc.perform(get(RETORNO).param("token_ws", "tok-" + codigo))
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("pedido=" + codigo)));
        assertThat(webpay.commits.get()).isEqualTo(commits);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pago p JOIN pedido x ON x.id = p.pedido_id WHERE x.codigo = ?",
                Integer.class, codigo)).isEqualTo(1);
    }

    @Test
    void retorno_rechazado_devuelveStockYCupos() throws Exception {
        long p = producto(4990, 5, null);
        long e = evento(7000, 10, null);
        String codigo = codigoDe(comprar(cliente, carrito("product", p, 2, "event", e, 1), UUID.randomUUID().toString()));
        webpay.programar("tok-" + codigo, Resultado.RECHAZADO);

        retorno("token_ws", "tok-" + codigo).andExpect(status().isSeeOther());
        assertThat(estado(codigo)).isEqualTo("RECHAZADO");
        assertThat(estadoTx(codigo)).isEqualTo("RECHAZADA");
        assertThat(estadoInscripcion(codigo)).isEqualTo("CANCELADA");
        assertThat(stock(p)).isEqualTo(5);
        assertThat(cuposOcupados(e)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimiento_stock WHERE referencia = ? AND tipo = 'ANULACION'",
                Integer.class, codigo)).isEqualTo(1);
    }

    @Test
    void retorno_anuladoPorElCliente_yTimeoutDelFormulario() throws Exception {
        long p = producto(4990, 5, null);
        String anulado = codigoDe(comprar(cliente, carrito("product", p, 1), UUID.randomUUID().toString()));
        String vencido = codigoDe(comprar(cliente, carrito("product", p, 1), UUID.randomUUID().toString()));

        retorno("TBK_TOKEN", "tok-" + anulado).andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("pedido=" + anulado)));
        assertThat(estado(anulado)).isEqualTo("ANULADO");
        assertThat(estadoTx(anulado)).isEqualTo("ANULADA_USUARIO");

        retorno("TBK_ORDEN_COMPRA", vencido).andExpect(status().isSeeOther());
        assertThat(estado(vencido)).isEqualTo("EXPIRADO");
        assertThat(estadoTx(vencido)).isEqualTo("EXPIRADA");
        assertThat(stock(p)).isEqualTo(5);
    }

    @Test
    void retorno_tokenDesconocido_redirigeConError() throws Exception {
        retorno("token_ws", "no-existe").andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/pago/resultado?error=pago")));
    }

    // ---------- vencimiento de reservas ----------

    @Test
    void vencimiento_sinPagoVenceYPagadoSeConfirma() throws Exception {
        long p = producto(4990, 5, null);
        long e = evento(7000, 10, null);
        String sinPago = codigoDe(comprar(cliente, carrito("product", p, 1, "event", e, 1), UUID.randomUUID().toString()));
        String pagado = codigoDe(comprar(cliente, carrito("product", p, 1), UUID.randomUUID().toString()));
        webpay.programar("tok-" + sinPago, Resultado.PENDIENTE);     // nunca terminó de pagar
        webpay.programar("tok-" + pagado, Resultado.AUTORIZADO);     // pagó, pero su retorno se perdió
        jdbc.update("UPDATE pedido SET expira_en = now() - interval '1 minute' WHERE codigo IN (?, ?)", sinPago, pagado);

        assertThat(checkout.expirarVencidos()).isGreaterThanOrEqualTo(2);

        assertThat(estado(sinPago)).isEqualTo("EXPIRADO");
        assertThat(estadoTx(sinPago)).isEqualTo("EXPIRADA");
        assertThat(estadoInscripcion(sinPago)).isEqualTo("EXPIRADA");
        assertThat(cuposOcupados(e)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimiento_stock WHERE referencia = ? AND tipo = 'RESERVA_EXPIRADA'",
                Integer.class, sinPago)).isEqualTo(1);
        assertThat(estado(pagado)).isEqualTo("PAGADO");
        assertThat(stock(p)).isEqualTo(4);   // solo queda descontada la unidad pagada
    }

    @Test
    void vencimiento_webpayCaido_noVenceTodavia() throws Exception {
        long p = producto(4990, 5, null);
        String codigo = codigoDe(comprar(cliente, carrito("product", p, 1), UUID.randomUUID().toString()));
        webpay.programar("tok-" + codigo, Resultado.CAIDA);
        jdbc.update("UPDATE pedido SET expira_en = now() - interval '1 minute' WHERE codigo = ?", codigo);

        checkout.expirarVencidos();
        assertThat(estado(codigo)).isEqualTo("PENDIENTE_PAGO");   // se reintenta en la próxima pasada
        webpay.programar("tok-" + codigo, Resultado.PENDIENTE);
    }

    // ---------- concurrencia: la última unidad ----------

    @Test
    void concurrencia_ultimaUnidad_soloUnClienteLaCompra() throws Exception {
        long p = producto(4990, 1, null);
        int clientes = 6;
        List<UsuarioConToken> compradores = new ArrayList<>();
        for (int i = 0; i < clientes; i++) {
            compradores.add(TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE));
        }
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(clientes);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (UsuarioConToken c : compradores) {
            resultados.add(pool.submit(() -> {
                largada.await();
                return comprar(c, carrito("product", p, 1), UUID.randomUUID().toString()).andReturn().getResponse().getStatus();
            }));
        }
        largada.countDown();
        List<Integer> codigos = new ArrayList<>();
        for (Future<Integer> r : resultados) {
            codigos.add(r.get());
        }
        pool.shutdown();

        assertThat(codigos).filteredOn(c -> c == 201).hasSize(1);
        assertThat(codigos).filteredOn(c -> c != 201).allMatch(c -> c == 409);
        assertThat(stock(p)).isZero();
    }

    // ---------- seguridad ----------

    @Test
    void seguridad_checkoutSoloClienteYRetornoPublico() throws Exception {
        long p = producto(4990, 5, null);
        String body = carrito("product", p, 1);
        comprar(null, body, UUID.randomUUID().toString()).andExpect(status().isUnauthorized());
        comprar(TokensDePrueba.crear(jdbc, encoder, Rol.VENDEDOR), body, UUID.randomUUID().toString())
                .andExpect(status().isForbidden());
        comprar(TokensDePrueba.crear(jdbc, encoder, Rol.ADMIN), body, UUID.randomUUID().toString())
                .andExpect(status().isForbidden());
        comprar(cliente, body, UUID.randomUUID().toString()).andExpect(status().isCreated());

        mvc.perform(post("/api/v1/carrito/validacion").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(get(RETORNO)).andExpect(status().isSeeOther());
    }
}
