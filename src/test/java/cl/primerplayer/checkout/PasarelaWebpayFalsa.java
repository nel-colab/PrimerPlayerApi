package cl.primerplayer.checkout;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import cl.primerplayer.pagos.api.PasarelaNoDisponibleException;
import cl.primerplayer.pagos.api.PasarelaWebpay;

/** Webpay simulado para las pruebas (sin internet). Cada token puede programarse con un resultado. */
public class PasarelaWebpayFalsa implements PasarelaWebpay {

    public enum Resultado { AUTORIZADO, RECHAZADO, SIN_RESULTADO, CAIDA, PENDIENTE }

    public static final String URL = "https://webpay.falso/initTransaction";

    private final Map<String, Long> montos = new ConcurrentHashMap<>();
    private final Map<String, String> ordenes = new ConcurrentHashMap<>();
    private final Map<String, Resultado> programados = new ConcurrentHashMap<>();
    public volatile boolean creacionCaida;
    public final java.util.concurrent.atomic.AtomicInteger commits = new java.util.concurrent.atomic.AtomicInteger();

    public void programar(String token, Resultado r) {
        programados.put(token, r);
    }

    @Override
    public InicioWebpay crear(String buyOrder, String sessionId, long monto, String urlRetorno) {
        if (creacionCaida) {
            throw new PasarelaNoDisponibleException("Webpay simulado caído");
        }
        String token = "tok-" + buyOrder;
        montos.put(token, monto);
        ordenes.put(token, buyOrder);
        return new InicioWebpay(token, URL);
    }

    @Override
    public Optional<ResultadoWebpay> confirmar(String token) {
        commits.incrementAndGet();
        return responder(token);
    }

    @Override
    public Optional<ResultadoWebpay> consultar(String token) {
        return responder(token);
    }

    @Override
    public String urlFormulario() {
        return URL;
    }

    private Optional<ResultadoWebpay> responder(String token) {
        Resultado r = programados.getOrDefault(token, Resultado.AUTORIZADO);
        long monto = montos.getOrDefault(token, 0L);
        return switch (r) {
            case CAIDA -> throw new PasarelaNoDisponibleException("Webpay simulado caído");
            case SIN_RESULTADO -> Optional.empty();
            case AUTORIZADO -> Optional.of(resultado(token, true, "AUTHORIZED", 0, monto));
            case RECHAZADO -> Optional.of(resultado(token, false, "FAILED", -1, monto));
            case PENDIENTE -> Optional.of(resultado(token, false, "INITIALIZED", -1, monto));
        };
    }

    private ResultadoWebpay resultado(String token, boolean ok, String status, int codigo, long monto) {
        return new ResultadoWebpay(ok, status, codigo, monto, ordenes.get(token), ok ? "123456" : null, "VD", 0,
                "6623", "1113", Instant.now(), "{\"status\":\"" + status + "\"}");
    }

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        PasarelaWebpayFalsa pasarelaWebpayFalsa() {
            return new PasarelaWebpayFalsa();
        }
    }
}
