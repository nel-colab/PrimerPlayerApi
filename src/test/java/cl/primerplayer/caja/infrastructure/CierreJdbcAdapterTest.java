package cl.primerplayer.caja.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import cl.primerplayer.caja.application.port.out.CierreRepositorio.Fila;

/** Serialización de subtotales y de la versión anterior (la consulta real la cubre CajaIntegracionTest). */
class CierreJdbcAdapterTest {

    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final CierreJdbcAdapter adapter = new CierreJdbcAdapter(jdbc);

    static final LocalDate DIA = LocalDate.of(2026, 11, 25);
    static final Instant CERRADO = Instant.parse("2026-11-26T00:30:00Z");

    static Fila fila(String observaciones, int version) {
        return new Fila(DIA, 50_000, 170_000, 120_000, 170_000, 300_000, 12, 0,
                new TreeMap<>(Map.of("debito", 180_000L, "efectivo", 120_000L)), observaciones, version, 3, CERRADO);
    }

    List<Object[]> llamadas() {
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(2)).update(anyString(), args.capture());
        return args.getAllValues();
    }

    @Test
    void jsonYMapaSonInversos() {
        Map<String, Long> m = new TreeMap<>(Map.of("efectivo", 120_000L, "cartas-tcg", -500L));

        String json = CierreJdbcAdapter.json(m);

        assertThat(json).isEqualTo("{\"cartas-tcg\": -500, \"efectivo\": 120000}");
        assertThat(CierreJdbcAdapter.mapa(json)).isEqualTo(m);
    }

    @Test
    void mapaDeNuloOVacio() {
        assertThat(CierreJdbcAdapter.mapa(null)).isEmpty();
        assertThat(CierreJdbcAdapter.mapa("{}")).isEmpty();
        assertThat(CierreJdbcAdapter.json(Map.of())).isEqualTo("{}");
    }

    @Test
    void reemplazarGuardaLaVersionAnteriorConObservacionesEscapadas() {
        adapter.reemplazar(fila("Faltó \"vuelto\"\ten\r\ncaja \\ ok\u0002", 1), fila("corregido", 2));

        List<Object[]> llamadas = llamadas();
        String datos = (String) llamadas.get(0)[2];
        assertThat(datos)
                .contains("\"fondoInicial\": 50000")
                .contains("\"subtotales\": {\"debito\": 180000, \"efectivo\": 120000}")
                .endsWith("\"observaciones\": \"Faltó \\\"vuelto\\\"\\ten\\r\\ncaja \\\\ ok\\u0002\"}");
        assertThat(llamadas.get(1)).contains(2, "corregido");
    }

    @Test
    void reemplazarConObservacionesNulasLasGuardaVacias() {
        adapter.reemplazar(fila(null, 1), fila("", 2));

        assertThat((String) llamadas().get(0)[2]).endsWith("\"observaciones\": \"\"}");
    }

    @Test
    void insertarDevuelveSiSeInsertoUnaFila() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1, 0);

        assertThat(adapter.insertar(fila("", 1))).isTrue();
        assertThat(adapter.insertar(fila("", 1))).isFalse();
    }

    @Test
    void contarSinResultadoEsCero() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(null, 7L);

        assertThat(adapter.contar()).isZero();
        assertThat(adapter.contar()).isEqualTo(7);
    }
}
