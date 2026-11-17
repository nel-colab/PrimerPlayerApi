package cl.primerplayer.identidad.web;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Cookie {@code pp_rt}: HttpOnly, SameSite=Strict y limitada a las rutas de autenticación. */
@Component
class RefreshCookie {

    static final String NOMBRE = "pp_rt";
    static final String RUTA = "/api/v1/auth";

    private final boolean segura;

    RefreshCookie(@Value("${pp.auth.cookie.segura:true}") boolean segura) {
        this.segura = segura;
    }

    ResponseCookie crear(String valor, Duration ttl) {
        return ResponseCookie.from(NOMBRE, valor)
                .httpOnly(true)
                .secure(segura)
                .sameSite("Strict")
                .path(RUTA)
                .maxAge(ttl)
                .build();
    }

    /** Misma cookie con Max-Age=0: el navegador la elimina (nombre, ruta y atributos deben coincidir). */
    ResponseCookie borrar() {
        return crear("", Duration.ZERO);
    }
}
