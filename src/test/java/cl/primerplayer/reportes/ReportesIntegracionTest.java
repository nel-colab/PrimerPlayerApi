package cl.primerplayer.reportes;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.io.ByteArrayInputStream;
import java.util.UUID;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import cl.primerplayer.TestcontainersConfiguration;
import cl.primerplayer.TokensDePrueba;
import cl.primerplayer.TokensDePrueba.UsuarioConToken;
import cl.primerplayer.shared.api.Rol;

/** Datos en 2018: ninguna otra prueba usa ese año, así los totales son exactos. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportesIntegracionTest {

    static final ZoneId CHILE = ZoneId.of("America/Santiago");
    static final LocalDate DIA = LocalDate.of(2018, 3, 15);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired PlatformTransactionManager txManager;

    UsuarioConToken admin, vendedor, cliente;

    @BeforeAll
    void datos() {
        admin = TokensDePrueba.crear(jdbc, encoder, Rol.ADMIN);
        vendedor = TokensDePrueba.crear(jdbc, encoder, Rol.VENDEDOR);
        cliente = TokensDePrueba.crear(jdbc, encoder, Rol.CLIENTE);
        long producto = jdbc.queryForObject("""
                INSERT INTO producto (nombre, precio, categoria_id) VALUES ('Sobre 2018', 5000,
                (SELECT id FROM categoria WHERE slug = 'tcg-pokemon')) RETURNING id""", Long.class);
        long evento = jdbc.queryForObject("""
                INSERT INTO evento (titulo, tipo, fecha_hora, lugar, precio, cupos_totales, descripcion)
                VALUES ('Torneo 2018', 'torneo-tcg', '2018-04-01T15:00:00-03:00', 'Tienda', 3000, 10, 'x') RETURNING id""", Long.class);

        new TransactionTemplate(txManager).executeWithoutResult(s -> {
            // DIA 12:30 — mostrador mixto: 2 sobres (10.000) + 1 cupo (3.000) = 13.000; efectivo 8.000 + débito 5.000
            long a = pedido(DIA, 12, 30, "tienda", "ENTREGADO", 13000);
            item(a, "product", producto, null, "Sobre 2018", 2, 5000);
            item(a, "event", null, evento, "Torneo 2018", 1, 3000);
            pago(a, "efectivo", 8000);
            pago(a, "debito", 5000);
            // DIA 18:10 — web: 1 sobre (5.000) con webpay
            long b = pedido(DIA, 18, 10, "web", "PAGADO", 5000);
            item(b, "product", producto, null, "Sobre 2018", 1, 5000);
            pago(b, "webpay", 5000);
            // DIA: una anulada (no cuenta)
            long c = pedido(DIA, 13, 0, "tienda", "ANULADO", 5000);
            item(c, "product", producto, null, "Sobre 2018", 1, 5000);
            pago(c, "efectivo", 5000);
            // Día anterior: 9.000 → variación (18.000 − 9.000) / 9.000 = +100 %
            long d = pedido(DIA.minusDays(1), 10, 0, "tienda", "ENTREGADO", 9000);
            item(d, "product", producto, null, "Sobre 2018", 1, 9000);
            pago(d, "efectivo", 9000);
            // Otro mes del mismo año (julio): 4.000
            long e = pedido(LocalDate.of(2018, 7, 2), 11, 0, "tienda", "ENTREGADO", 4000);
            item(e, "product", producto, null, "Sobre 2018", 1, 4000);
            pago(e, "efectivo", 4000);
        });
    }

    long pedido(LocalDate dia, int hora, int minuto, String canal, String estado, long total) {
        var ts = dia.atTime(hora, minuto).atZone(CHILE).toOffsetDateTime();
        return jdbc.queryForObject("""
                INSERT INTO pedido (codigo, cliente_id, vendedor_id, canal, estado, total, pagado_en, creado_en, actualizado_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class,
                "TR" + UUID.randomUUID().toString().substring(0, 10), cliente.id(),
                "tienda".equals(canal) ? vendedor.id() : null, canal, estado, total, ts, ts, ts);
    }

    void item(long pedido, String tipo, Long producto, Long evento, String nombre, int cantidad, long precio) {
        jdbc.update("""
                INSERT INTO pedido_item (pedido_id, tipo, producto_id, evento_id, nombre, cantidad, precio_unitario)
                VALUES (?, ?, ?, ?, ?, ?, ?)""", pedido, tipo, producto, evento, nombre, cantidad, precio);
    }

    void pago(long pedido, String metodo, long monto) {
        jdbc.update("""
                INSERT INTO pago (pedido_id, metodo, monto, numero_boleta, efectivo_recibido, vuelto_entregado)
                VALUES (?, ?, ?, ?, ?, ?)""", pedido, metodo, monto, "debito".equals(metodo) ? "B-1" : null,
                "efectivo".equals(metodo) ? monto : null, "efectivo".equals(metodo) ? 0L : null);
    }

    @Test
    void dia_totalesVariacionMetodosYTop() throws Exception {
        mvc.perform(get("/api/v1/admin/reportes/dia").param("fecha", DIA.toString())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(18000))
                .andExpect(jsonPath("$.totalDiaAnterior").value(9000))
                .andExpect(jsonPath("$.variacion").value(100.0))
                .andExpect(jsonPath("$.ordenes").value(2))
                .andExpect(jsonPath("$.ticketPromedio").value(9000))
                .andExpect(jsonPath("$.montoProductos").value(15000))
                .andExpect(jsonPath("$.montoEventos").value(3000))
                .andExpect(jsonPath("$.totalTienda").value(13000))
                .andExpect(jsonPath("$.totalWeb").value(5000))
                .andExpect(jsonPath("$.porMetodo[?(@.metodo == 'efectivo')].monto").value(8000))
                .andExpect(jsonPath("$.porMetodo[?(@.metodo == 'debito')].monto").value(5000))
                .andExpect(jsonPath("$.porMetodo[?(@.metodo == 'webpay')].ordenes").value(1))
                .andExpect(jsonPath("$.top5[0].nombre").value("Sobre 2018"))
                .andExpect(jsonPath("$.top5[0].unidades").value(3))
                .andExpect(jsonPath("$.top5[0].monto").value(15000));
    }

    @Test
    void porHora_24TramosEnHoraDeChile() throws Exception {
        mvc.perform(get("/api/v1/admin/reportes/por-hora").param("fecha", DIA.toString())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$", hasSize(24)))
                .andExpect(jsonPath("$[12].monto").value(13000))
                .andExpect(jsonPath("$[18].monto").value(5000))
                .andExpect(jsonPath("$[13].monto").value(0));   // la anulada no cuenta
    }

    @Test
    void porDiaYPorMes() throws Exception {
        mvc.perform(get("/api/v1/admin/reportes/por-dia").param("mes", "2018-03")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.tramos", hasSize(31)))
                .andExpect(jsonPath("$.tramos[13].monto").value(9000))    // día 14
                .andExpect(jsonPath("$.tramos[14].monto").value(18000))   // día 15
                .andExpect(jsonPath("$.total").value(27000));
        mvc.perform(get("/api/v1/admin/reportes/por-mes").param("anio", "2018")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(jsonPath("$.tramos", hasSize(12)))
                .andExpect(jsonPath("$.tramos[2].monto").value(27000))
                .andExpect(jsonPath("$.tramos[6].monto").value(4000))
                .andExpect(jsonPath("$.total").value(31000))
                .andExpect(jsonPath("$.ordenes").value(4));
    }

    @Test
    void porDiaYPorMes_exportanExcelPorParametroYNegociacion() throws Exception {
        var mensual = mvc.perform(get("/api/v1/admin/reportes/por-dia")
                        .param("year", "2018").param("month", "3").param("format", "excel")
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"ventas-2018-03.xlsx\""))
                .andReturn().getResponse();
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(mensual.getContentAsByteArray()))) {
            org.assertj.core.api.Assertions.assertThat(workbook.getSheetName(0)).isEqualTo("Resumen del mes");
            org.assertj.core.api.Assertions.assertThat(workbook.getSheetName(1)).isEqualTo("Por día");
        }

        mvc.perform(get("/api/v1/admin/reportes/por-mes").param("year", "2018")
                        .header(HttpHeaders.ACCEPT, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"ventas-2018.xlsx\""));
    }

    @Test
    void validaciones() throws Exception {
        mvc.perform(get("/api/v1/admin/reportes/dia").param("fecha", "15-03-2018").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/reportes/por-dia").param("mes", "2018-13").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void seguridad_soloAdmin() throws Exception {
        mvc.perform(get("/api/v1/admin/reportes/dia").param("fecha", DIA.toString())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/reportes/dia").param("fecha", DIA.toString())
                .header(HttpHeaders.AUTHORIZATION, vendedor.bearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/reportes/dia").param("fecha", DIA.toString())
                .header(HttpHeaders.AUTHORIZATION, cliente.bearer())).andExpect(status().isForbidden());
    }

    @Test
    void auditoria_filtrosJsonYSoloAdmin() throws Exception {
        String entidad = "prueba-" + UUID.randomUUID().toString().substring(0, 6);
        jdbc.update("""
                INSERT INTO auditoria (ocurrido_en, usuario_id, accion, entidad, entidad_id, antes, despues, ip)
                VALUES (now(), ?, 'PRECIO_CAMBIADO', ?, '42', '{"precio": 5990}', '{"precio": 4990}', '10.0.0.1')""",
                admin.id(), entidad);
        mvc.perform(get("/api/v1/admin/auditoria").param("entidad", entidad).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("PRECIO_CAMBIADO"))
                .andExpect(jsonPath("$.content[0].before.precio").value(5990))
                .andExpect(jsonPath("$.content[0].after.precio").value(4990))
                .andExpect(jsonPath("$.content[0].userName").value("Prueba ADMIN"))
                .andExpect(jsonPath("$.content[0].ip").value("10.0.0.1"));
        mvc.perform(get("/api/v1/admin/auditoria").header(HttpHeaders.AUTHORIZATION, vendedor.bearer()))
                .andExpect(status().isForbidden());
    }
}
