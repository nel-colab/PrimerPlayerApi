package cl.primerplayer.eventos.api;

import java.util.List;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 409 {@code event-full} (no alcanzan los cupos) o {@code event-past} (el evento ya ocurrió). */
public class CuposInsuficientesException extends ExcepcionNegocio {

    private CuposInsuficientesException(String code, String detalle, int linea, long eventoId) {
        super(code, HttpStatus.CONFLICT, detalle, List.of(new ErrorLinea(linea, "event", eventoId, code, null)));
    }

    public static CuposInsuficientesException lleno(int linea, long eventoId, int disponibles) {
        return new CuposInsuficientesException("event-full",
                "No quedan cupos suficientes para el evento " + eventoId + " (disponibles: " + disponibles + ").",
                linea, eventoId);
    }

    public static CuposInsuficientesException pasado(int linea, long eventoId) {
        return new CuposInsuficientesException("event-past", "El evento " + eventoId + " ya ocurrió.", linea, eventoId);
    }
}
