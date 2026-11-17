package cl.primerplayer.catalogo.infrastructure.persistence;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import cl.primerplayer.catalogo.api.ConsultarCategoriasUseCase.NodoCategoria;
import cl.primerplayer.catalogo.application.port.out.MaestrosCatalogoRepositorio;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class MaestrosPersistenceAdapter implements MaestrosCatalogoRepositorio {

    private final CategoriaJpaRepository categorias;
    private final FranquiciaJpaRepository franquicias;

    @Override
    public Optional<Boolean> categoriaEsHoja(String slug) {
        return categorias.findBySlug(slug).map(CategoriaEntity::isEsHoja);
    }

    @Override
    public boolean existeFranquicia(String nombre) {
        return franquicias.findByNombreIgnoreCase(nombre).isPresent();
    }

    @Override
    public List<NodoCategoria> arbolCategorias() {
        List<CategoriaEntity> todas = categorias.findAll(Sort.by("orden", "id"));
        Map<Short, List<CategoriaEntity>> hijos = todas.stream().filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(CategoriaEntity::getParentId));
        return todas.stream().filter(c -> c.getParentId() == null).map(c -> nodo(c, hijos)).toList();
    }

    private static NodoCategoria nodo(CategoriaEntity c, Map<Short, List<CategoriaEntity>> hijos) {
        List<NodoCategoria> sub = hijos.getOrDefault(c.getId(), List.of()).stream()
                .sorted(Comparator.comparingInt(CategoriaEntity::getOrden))
                .map(h -> nodo(h, hijos)).toList();
        return new NodoCategoria(c.getSlug(), c.getTitulo(), c.isEsHoja(), sub);
    }

    @Override
    public List<String> franquicias() {
        return franquicias.findAll(Sort.by("nombre")).stream().map(FranquiciaEntity::getNombre).toList();
    }
}
