package cl.primerplayer.catalogo.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.catalogo.api.ConsultarCategoriasUseCase;
import cl.primerplayer.catalogo.application.port.out.MaestrosCatalogoRepositorio;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class CategoriasService implements ConsultarCategoriasUseCase {

    private final MaestrosCatalogoRepositorio maestros;

    @Override
    @Transactional(readOnly = true)
    public List<NodoCategoria> arbol() {
        return maestros.arbolCategorias();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> franquicias() {
        return maestros.franquicias();
    }
}
