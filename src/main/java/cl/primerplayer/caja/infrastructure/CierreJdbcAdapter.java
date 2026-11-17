package cl.primerplayer.caja.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import cl.primerplayer.caja.application.port.out.CierreRepositorio;
import lombok.RequiredArgsConstructor;

/** Adaptador JDBC de los cierres. {@code subtotales} se guarda como JSONB {@code {"efectivo": 120000, ...}}. */
@Component
@RequiredArgsConstructor
class CierreJdbcAdapter implements CierreRepositorio {

    private static final Pattern PAR = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(-?\\d+)");

    private final JdbcTemplate jdbc;

    @Override
    public Optional<Fila> porFecha(LocalDate fecha, boolean bloquear) {
        return jdbc.query("SELECT * FROM cierre_caja WHERE fecha = ?" + (bloquear ? " FOR UPDATE" : ""),
                (rs, n) -> fila(rs), fecha).stream().findFirst();
    }

    @Override
    public boolean insertar(Fila f) {
        return jdbc.update("""
                INSERT INTO cierre_caja (fecha, fondo_inicial, efectivo_contado, efectivo_neto, esperado_en_caja,
                                         total_ventas, numero_ventas, diferencia, subtotales, observaciones, version,
                                         cerrado_por, cerrado_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, 1, ?, ?)
                ON CONFLICT (fecha) DO NOTHING
                """, f.fecha(), f.fondoInicial(), f.efectivoContado(), f.efectivoNeto(), f.esperadoEnCaja(),
                f.totalVentas(), f.numeroVentas(), f.diferencia(), json(f.subtotales()), f.observaciones(),
                f.cerradoPor(), f.cerradoEn().atOffset(ZoneOffset.UTC)) == 1;
    }

    @Override
    public void reemplazar(Fila a, Fila n) {
        jdbc.update("""
                INSERT INTO cierre_caja_version (fecha, version, datos, cerrado_por, cerrado_en)
                VALUES (?, ?, CAST(? AS jsonb), ?, ?)
                """, a.fecha(), a.version(), datos(a), a.cerradoPor(), a.cerradoEn().atOffset(ZoneOffset.UTC));
        jdbc.update("""
                UPDATE cierre_caja SET fondo_inicial = ?, efectivo_contado = ?, efectivo_neto = ?, esperado_en_caja = ?,
                       total_ventas = ?, numero_ventas = ?, diferencia = ?, subtotales = CAST(? AS jsonb),
                       observaciones = ?, version = ?, cerrado_por = ?, cerrado_en = ?
                WHERE fecha = ?
                """, n.fondoInicial(), n.efectivoContado(), n.efectivoNeto(), n.esperadoEnCaja(), n.totalVentas(),
                n.numeroVentas(), n.diferencia(), json(n.subtotales()), n.observaciones(), n.version(), n.cerradoPor(),
                n.cerradoEn().atOffset(ZoneOffset.UTC), n.fecha());
    }

    @Override
    public List<Fila> pagina(int page, int size) {
        return jdbc.query("SELECT * FROM cierre_caja ORDER BY fecha DESC LIMIT ? OFFSET ?",
                (rs, n) -> fila(rs), size, (long) page * size);
    }

    @Override
    public long contar() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM cierre_caja", Long.class);
        return n == null ? 0 : n;
    }

    // ---------- mapeo ----------

    private static Fila fila(ResultSet rs) throws SQLException {
        return new Fila(rs.getObject("fecha", LocalDate.class), rs.getLong("fondo_inicial"), rs.getLong("efectivo_contado"),
                rs.getLong("efectivo_neto"), rs.getLong("esperado_en_caja"), rs.getLong("total_ventas"),
                rs.getInt("numero_ventas"), rs.getLong("diferencia"), mapa(rs.getString("subtotales")),
                rs.getString("observaciones"), rs.getInt("version"), rs.getLong("cerrado_por"),
                rs.getObject("cerrado_en", OffsetDateTime.class).toInstant());
    }

    /** Las claves son métodos de pago (texto fijo, sin comillas): un JSON simple basta. */
    static String json(Map<String, Long> m) {
        return m.entrySet().stream().map(e -> "\"" + e.getKey() + "\": " + e.getValue())
                .collect(Collectors.joining(", ", "{", "}"));
    }

    static Map<String, Long> mapa(String json) {
        Map<String, Long> m = new TreeMap<>();
        if (json != null) {
            Matcher x = PAR.matcher(json);
            while (x.find()) {
                m.put(x.group(1), Long.parseLong(x.group(2)));
            }
        }
        return m;
    }

    /** Foto completa de la versión anterior (mismas claves que la API, para leerla sin traducir). */
    private static String datos(Fila f) {
        return """
                {"fondoInicial": %d, "efectivoContado": %d, "efectivoNeto": %d, "esperadoEnCaja": %d, \
                "totalVentas": %d, "numeroVentas": %d, "diferencia": %d, "subtotales": %s, "observaciones": %s}"""
                .formatted(f.fondoInicial(), f.efectivoContado(), f.efectivoNeto(), f.esperadoEnCaja(), f.totalVentas(),
                        f.numeroVentas(), f.diferencia(), json(f.subtotales()), textoJson(f.observaciones()));
    }

    private static String textoJson(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : (s == null ? "" : s).toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }
}
