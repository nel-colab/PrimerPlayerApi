package cl.primerplayer.ventas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import cl.primerplayer.TestcontainersConfiguration;
import cl.primerplayer.TokensDePrueba;
import cl.primerplayer.TokensDePrueba.UsuarioConToken;
import cl.primerplayer.notificaciones.CorreosCapturados;
import cl.primerplayer.shared.api.Rol;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, CorreosCapturados.Config.class})
@ActiveProfiles("test")
class PedidosIntegracionTest {

    static final String ME = "/api/v1/me/pedidos";
    static final String ADMIN = "/api/v1/admin/pedidos";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired CorreosCapturados correos;

    UsuarioConToken cliente, otroCliente, vendedor, admin;
    long productoId, eventoId;
    /** Prefijo único de código por prueba: la base es compartida entre clases. */
    String prefijo;

    @BeforeEach
    void datos() {
        cliente = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
        otroCliente = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
        vendedor = TokensDePrueba.crear(jdbc, encoder, Rol.VENDEDOR);
        admin = TokensDePrueba.crear(jdbc, encoder, Rol.ADMIN);
        prefijo = "T" + UUID.randomUUID().toString().substring(0, 6).toUpperCase() + "-";
        productoId = jdbc.queryForObject("""
                INSERT INTO producto (nombre, precio, categoria_id, publicado)
                VALUES ('Sobre de prueba', 4990, (SELECT id FROM categoria WHERE slug = 'tcg-pokemon'), true) RETURNING id
                """, Long.class);
        eventoId = jdbc.queryForObject("""
                INSERT INTO evento (titulo, tipo, fecha_hora, lugar, precio, cupos_totales, descripcion, publicado)
                VALUES ('Torneo de prueba', 'torneo-tcg', now() + interval '10 days', 'Tienda', 7000, 10, 'x', true) RETURNING id
                """, Long.class);
    }

    /** Pedido web o de tienda con un producto (y opcionalmente un evento). */
    String pedido(String sufijo, UsuarioConToken dueno, String canal, String estado, boolean conProducto,
                  boolean conEvento, Instant creadoEn) {
        String codigo = prefijo + sufijo;
        long total = (conProducto ? 2 * 4990 : 0) + (conEvento ? 7000 : 0);
        Long id = jdbc.queryForObject("""
                INSERT INTO pedido (codigo, cliente_id, vendedor_id, canal, estado, total, pagado_en, creado_en, actualizado_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, codigo, dueno.id(), "tienda".equals(canal) ? vendedor.id() : null, canal, estado, total,
                java.sql.Timestamp.from(creadoEn), java.sql.Timestamp.from(creadoEn), java.sql.Timestamp.from(creadoEn));
        if (conProducto) {
            jdbc.update("INSERT INTO pedido_item (pedido_id, tipo, producto_id, nombre, cantidad, precio_unitario) VALUES (?, 'product', ?, 'Sobre de prueba', 2, 4990)",
                    id, productoId);
        }
        if (conEvento) {
            jdbc.update("INSERT INTO pedido_item (pedido_id, tipo, evento_id, nombre, cantidad, precio_unitario) VALUES (?, 'event', ?, 'Torneo de prueba', 1, 7000)",
                    id, eventoId);
        }
        return codigo;
    }

    String web(String sufijo, String estado) {
        return pedido(sufijo, cliente, "web", estado, true, false, Instant.now());
    }

    ResultActions como(UsuarioConToken u, MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(u == null ? req : req.header(HttpHeaders.AUTHORIZATION, u.bearer()));
    }

    ResultActions marcar(UsuarioConToken u, String codigo, String estado) throws Exception {
        return marcar(u, codigo, estado, false);
    }

    ResultActions marcar(UsuarioConToken u, String codigo, String estado, boolean notificarCliente) throws Exception {
        return como(u, patch(ADMIN + "/" + codigo + "/estado").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"" + estado + "\",\"notificarCliente\":" + notificarCliente + "}"));
    }

    // ---------- mis pedidos ----------

    @Test
    void misPedidos_soloLosPropiosConFormaDelFrontend() throws Exception {
        String mio = pedido("A", cliente, "web", "PAGADO", true, true, Instant.now());
        pedido("B", otroCliente, "web", "PAGADO", true, false, Instant.now());

        como(cliente, get(ME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value(mio))
                .andExpect(jsonPath("$.content[0].status").value("PAGADO"))
                .andExpect(jsonPath("$.content[0].channel").value("web"))
                .andExpect(jsonPath("$.content[0].total").value(16980))
                .andExpect(jsonPath("$.content[0].email").value(cliente.email()))
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.content[0].items[*].type", contains("product", "event")))
                .andExpect(jsonPath("$.content[0].items[0].itemId").value((int) productoId))
                .andExpect(jsonPath("$.content[0].items[0].quantity").value(2))
                .andExpect(jsonPath("$.content[0].items[0].unitPrice").value(4990))
                .andExpect(jsonPath("$.content[0].items[0].subtotal").value(9980));
    }

    @Test
    void miPedido_detallePropio200_ajeno404() throws Exception {
        String mio = web("A", "PAGADO");
        String ajeno = pedido("B", otroCliente, "web", "PAGADO", true, false, Instant.now());

        como(cliente, get(ME + "/" + mio)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(mio));
        como(cliente, get(ME + "/" + ajeno))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("item-unavailable"));
    }

    // ---------- panel de la tienda ----------

    @Test
    void panel_filtrosPorCodigoCanalEstadoYVendedor() throws Exception {
        String w1 = web("W1", "PAGADO");
        String w2 = web("W2", "LISTO_RETIRO");
        String t1 = pedido("T1", cliente, "tienda", "ENTREGADO", true, false, Instant.now());

        como(vendedor, get(ADMIN).param("codigo", prefijo.toLowerCase()))
                .andExpect(jsonPath("$.content[*].code", containsInAnyOrder(w1, w2, t1)));
        como(vendedor, get(ADMIN).param("codigo", prefijo).param("canal", "tienda"))
                .andExpect(jsonPath("$.content[*].code", contains(t1)))
                .andExpect(jsonPath("$.content[0].seller").value("Prueba VENDEDOR"));
        como(vendedor, get(ADMIN).param("codigo", prefijo).param("estado", "PAGADO", "listo-retiro"))
                .andExpect(jsonPath("$.content[*].code", containsInAnyOrder(w1, w2)));
        como(vendedor, get(ADMIN).param("codigo", prefijo).param("vendedor", String.valueOf(vendedor.id())))
                .andExpect(jsonPath("$.content[*].code", contains(t1)));
    }

    @Test
    void panel_incluyeTelefonoYRutDelCliente_yNullEnVentasDeMostrador() throws Exception {
        jdbc.update("UPDATE usuario SET telefono = ?, rut = ? WHERE id = ?",
                "+56 9 1234 5678", "12.345.678-5", cliente.id());
        String web = pedido("CONTACTO", cliente, "web", "PAGADO", true, false, Instant.now());
        String tienda = pedido("MOSTRADOR", cliente, "tienda", "ENTREGADO", true, false, Instant.now());

        como(vendedor, get(ADMIN).param("codigo", web))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].phone").value("+56 9 1234 5678"))
                .andExpect(jsonPath("$.content[0].rut").value("12.345.678-5"));
        como(vendedor, get(ADMIN).param("codigo", tienda))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].phone").value(nullValue()))
                .andExpect(jsonPath("$.content[0].rut").value(nullValue()));
    }

    @Test
    void panel_rangoDeFechasEnDiasDeChile() throws Exception {
        Instant hace10Dias = Instant.now().minus(10, ChronoUnit.DAYS);
        String viejo = pedido("V", cliente, "web", "ENTREGADO", true, false, hace10Dias);
        String nuevo = web("N", "PAGADO");
        String hoy = java.time.LocalDate.now(java.time.ZoneId.of("America/Santiago")).toString();

        como(vendedor, get(ADMIN).param("codigo", prefijo).param("desde", hoy).param("hasta", hoy))
                .andExpect(jsonPath("$.content[*].code", contains(nuevo)));
        como(vendedor, get(ADMIN).param("codigo", prefijo)
                        .param("hasta", java.time.LocalDate.now(java.time.ZoneId.of("America/Santiago")).minusDays(5).toString()))
                .andExpect(jsonPath("$.content[*].code", contains(viejo)));
        como(vendedor, get(ADMIN).param("desde", hoy).param("hasta", "2020-01-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
    }

    // ---------- retiro en tienda ----------

    @Test
    void retiro_pagadoListoEntregado() throws Exception {
        String codigo = web("R", "PAGADO");

        marcar(vendedor, codigo, "LISTO_RETIRO").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("LISTO_RETIRO"));
        marcar(vendedor, codigo, "ENTREGADO").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ENTREGADO"));
        assertThat(jdbc.queryForObject("SELECT estado FROM pedido WHERE codigo = ?", String.class, codigo)).isEqualTo("ENTREGADO");

        // Ya entregado: no se puede volver a marcar
        marcar(vendedor, codigo, "LISTO_RETIRO")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("order-invalid-state"));
    }

    @Test
    void retiro_casosNoPermitidos() throws Exception {
        String pendiente = web("P", "PENDIENTE_PAGO");
        String soloEvento = pedido("E", cliente, "web", "PAGADO", false, true, Instant.now());
        String mostrador = pedido("M", cliente, "tienda", "PAGADO", true, false, Instant.now());

        for (String codigo : List.of(pendiente, mostrador)) {
            marcar(vendedor, codigo, "ENTREGADO")
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("order-invalid-state"));
        }
        marcar(vendedor, soloEvento, "LISTO_RETIRO")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("LISTO_RETIRO"));
        marcar(vendedor, web("X", "PAGADO"), "REEMBOLSADO")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
        marcar(vendedor, web("Y", "PAGADO"), "NO-EXISTE")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
        marcar(vendedor, prefijo + "INEXISTENTE", "ENTREGADO")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("item-unavailable"));
    }

    @Test
    void retiro_notificarCliente_enviaCorreoConCodigoItemsYDireccion() throws Exception {
        String codigo = pedido("MAIL", cliente, "web", "PAGADO", false, true, Instant.now());

        marcar(vendedor, codigo, "LISTO_RETIRO", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LISTO_RETIRO"));

        await().atMost(Duration.ofSeconds(5)).until(() -> correos.para(cliente.email()).stream()
                .anyMatch(c -> c.asunto().contains("está listo para retirar")));
        var correo = correos.para(cliente.email()).stream()
                .filter(c -> c.asunto().contains("está listo para retirar")).findFirst().orElseThrow();
        assertThat(correo.asunto()).contains(codigo);
        assertThat(correo.texto()).contains(codigo, "Torneo de prueba", "Av. Portales Ote. 1126, San Bernardo",
                "Preséntate con este código al llegar a la tienda.");
    }

    @Test
    void retiro_concurrente_soloUnVendedorLoEntrega() throws Exception {
        String codigo = web("C", "PAGADO");
        int hilos = 8;
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            resultados.add(pool.submit(() -> {
                largada.await();
                return marcar(vendedor, codigo, "ENTREGADO").andReturn().getResponse().getStatus();
            }));
        }
        largada.countDown();
        List<Integer> codigos = new ArrayList<>();
        for (Future<Integer> r : resultados) {
            codigos.add(r.get());
        }
        pool.shutdown();

        assertThat(codigos).filteredOn(c -> c == 200).hasSize(1);
        assertThat(codigos).filteredOn(c -> c != 200).allMatch(c -> c == 409);
    }

    // ---------- seguridad: rol × endpoint ----------

    @Test
    void seguridad_misPedidosSoloCliente() throws Exception {
        como(null, get(ME)).andExpect(status().isUnauthorized());
        como(cliente, get(ME)).andExpect(status().isOk());
        como(vendedor, get(ME)).andExpect(status().isForbidden());
        como(admin, get(ME)).andExpect(status().isForbidden());

        String codigo = web("S", "PAGADO");
        como(null, get(ME + "/" + codigo)).andExpect(status().isUnauthorized());
        como(vendedor, get(ME + "/" + codigo)).andExpect(status().isForbidden());
    }

    @Test
    void seguridad_panelSoloVendedorYAdmin() throws Exception {
        como(null, get(ADMIN)).andExpect(status().isUnauthorized());
        como(cliente, get(ADMIN)).andExpect(status().isForbidden());
        como(vendedor, get(ADMIN)).andExpect(status().isOk());
        como(admin, get(ADMIN)).andExpect(status().isOk());

        String codigo = web("S", "PAGADO");
        marcar(null, codigo, "LISTO_RETIRO").andExpect(status().isUnauthorized());
        marcar(cliente, codigo, "LISTO_RETIRO").andExpect(status().isForbidden());
        marcar(admin, codigo, "LISTO_RETIRO").andExpect(status().isOk());
        marcar(vendedor, codigo, "ENTREGADO").andExpect(status().isOk());
    }
}
