package cl.primerplayer.auditoria.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.auditoria.api.ConsultarAuditoriaUseCase;
import cl.primerplayer.auditoria.infrastructure.AuditoriaRepositorio;
import cl.primerplayer.identidad.api.ConsultarUsuarioUseCase;
import cl.primerplayer.identidad.api.UsuarioResumen;
import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.ValidacionException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class AuditoriaService implements ConsultarAuditoriaUseCase {

    static final int MAX_PAGE_SIZE = 100;

    private final AuditoriaRepositorio auditoria;
    private final ConsultarUsuarioUseCase usuarios;

    @Override
    @Transactional(readOnly = true)
    public Pagina<Registro> buscar(String entidad, String entidadId, Instant desde, Instant hasta, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ValidacionException("size", "page >= 0 y size entre 1 y " + MAX_PAGE_SIZE + ".");
        }
        var filtro = new AuditoriaRepositorio.Filtro(vacio(entidad), vacio(entidadId), desde, hasta);
        List<Registro> filas = auditoria.buscar(filtro, page, size);
        Map<Long, UsuarioResumen> personas = usuarios.buscarPorIds(
                filas.stream().map(Registro::usuarioId).filter(Objects::nonNull).distinct().toList());
        return Pagina.de(filas.stream().map(r -> {
            UsuarioResumen u = r.usuarioId() == null ? null : personas.get(r.usuarioId());
            return new Registro(r.id(), r.ocurridoEn(), r.usuarioId(), u == null ? null : u.nombre(), r.accion(),
                    r.entidad(), r.entidadId(), r.antes(), r.despues(), r.ip());
        }).toList(), page, size, auditoria.contar(filtro));
    }

    private static String vacio(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
