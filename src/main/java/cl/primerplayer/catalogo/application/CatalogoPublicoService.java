package cl.primerplayer.catalogo.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.catalogo.api.ConsultarCatalogoPublicoUseCase;
import cl.primerplayer.catalogo.api.ProductoPublico;
import cl.primerplayer.catalogo.application.port.out.CatalogoPublicoRepositorio;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import cl.primerplayer.shared.api.ValidacionException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class CatalogoPublicoService implements ConsultarCatalogoPublicoUseCase {

    private final CatalogoPublicoRepositorio catalogo;

    @Override
    @Transactional(readOnly = true)
    public ResultadoCatalogo buscar(FiltroCatalogo filtro) {
        return catalogo.buscar(filtro);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoPublico> destacados(int limite) {
        validarLimite(limite, MAX_DESTACADOS);
        return catalogo.masPopulares(limite);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductoPublico obtener(long id) {
        return catalogo.visible(id).orElseThrow(CatalogoPublicoService::noDisponible);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<Long, ProductoPublico> visibles(java.util.Collection<Long> ids) {
        if (ids.isEmpty()) {
            return java.util.Map.of();
        }
        return catalogo.visibles(ids).stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(ProductoPublico::id, p -> p));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoPublico> relacionados(long id, int limite) {
        validarLimite(limite, MAX_RELACIONADOS);
        if (catalogo.visible(id).isEmpty()) {
            throw noDisponible();
        }
        return catalogo.relacionados(id, limite);
    }

    private static void validarLimite(int limite, int maximo) {
        if (limite < 1 || limite > maximo) {
            throw new ValidacionException("limit", "Debe estar entre 1 y " + maximo + ".");
        }
    }

    private static RecursoNoEncontradoException noDisponible() {
        return new RecursoNoEncontradoException("El producto no existe o no está disponible.");
    }
}
