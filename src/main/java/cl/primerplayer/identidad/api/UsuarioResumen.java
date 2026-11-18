package cl.primerplayer.identidad.api;

import cl.primerplayer.shared.api.Rol;

public record UsuarioResumen(long id, String email, String nombre, Rol rol, String telefono, String rut) {

    public UsuarioResumen(long id, String email, String nombre, Rol rol) {
        this(id, email, nombre, rol, null, null);
    }
}
