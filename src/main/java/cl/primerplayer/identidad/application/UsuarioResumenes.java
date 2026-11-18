package cl.primerplayer.identidad.application;

import cl.primerplayer.identidad.api.UsuarioResumen;
import cl.primerplayer.identidad.domain.Usuario;

final class UsuarioResumenes {

    private UsuarioResumenes() {}

    static UsuarioResumen de(Usuario u) {
        return new UsuarioResumen(u.id(), u.email(), u.nombre(), u.rol(), u.telefono(), u.rut());
    }
}
