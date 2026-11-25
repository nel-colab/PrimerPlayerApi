package cl.primerplayer.identidad.application;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.identidad.api.ConsultarUsuarioUseCase;
import cl.primerplayer.identidad.api.UsuarioResumen;
import cl.primerplayer.identidad.application.port.out.UsuarioRepositorio;
import cl.primerplayer.identidad.domain.Usuario;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class ConsultarUsuarioService implements ConsultarUsuarioUseCase {

    private final UsuarioRepositorio usuarios;
    private final java.time.Clock clock;

    @Override
    @Transactional(readOnly = true)
    public java.util.List<UsuarioResumen> administradoresActivos() {
        return usuarios.administradoresActivos(clock.instant()).stream().map(UsuarioResumenes::de).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UsuarioResumen> buscarActivo(long id) {
        return usuarios.buscarPorId(id)
                .filter(Usuario::puedeIniciarSesion)
                .map(UsuarioResumenes::de);
    }

    @Override
    @Transactional(readOnly = true)
    public long clienteMostradorId() {
        return usuarios.buscarPorEmail("mostrador@primerplayer.cl")
                .filter(Usuario::anonimo)
                .map(Usuario::id)
                .orElseThrow(() -> new IllegalStateException(
                        "Falta el cliente de mostrador (V6). Ejecuta scripts/db/03-datos-base.sql."));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean puedeComprar(long id) {
        return estadoParaComprar(id) == EstadoCompra.PERMITIDO;
    }

    @Override
    @Transactional(readOnly = true)
    public EstadoCompra estadoParaComprar(long id) {
        var u = usuarios.buscarPorId(id).orElse(null);
        if (u == null || !u.activo() || u.anonimo() || u.eliminadoEn() != null) {
            return EstadoCompra.NO_DISPONIBLE;
        }
        if (usuarios.enListaNegra(id)) {
            return EstadoCompra.LISTA_NEGRA;
        }
        return u.puedeComprar() ? EstadoCompra.PERMITIDO : EstadoCompra.SIN_VERIFICAR;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<Long, UsuarioResumen> buscarPorIds(java.util.Collection<Long> ids) {
        if (ids.isEmpty()) {
            return java.util.Map.of();
        }
        return usuarios.buscarPorIds(ids).stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Usuario::id, UsuarioResumenes::de));
    }
}
