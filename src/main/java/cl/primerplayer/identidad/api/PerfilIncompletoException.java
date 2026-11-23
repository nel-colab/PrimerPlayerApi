package cl.primerplayer.identidad.api;

import java.util.List;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/**
 * 403 {@code profile-incomplete}: un VENDEDOR o ADMIN sin nombre, teléfono o RUT no puede usar {@code /admin/**}.
 * Lo completa en {@code PUT /api/v1/me/perfil}.
 */
public class PerfilIncompletoException extends ExcepcionNegocio {

    public PerfilIncompletoException(List<String> faltantes) {
        super("profile-incomplete", HttpStatus.FORBIDDEN,
                "Completa tu información personal para trabajar en el panel. Falta: " + String.join(", ", faltantes) + ".");
    }
}
