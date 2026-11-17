package cl.primerplayer.eventos.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 409 {@code slot-taken}: el bloque horario choca con un cumpleaños o, si es cumpleaños, con otro evento. */
public class BloqueOcupadoException extends ExcepcionNegocio {
    public BloqueOcupadoException() {
        super("slot-taken", HttpStatus.CONFLICT,
              "El horario se traslapa con otro evento o con un cumpleaños ya reservado.");
    }
}
