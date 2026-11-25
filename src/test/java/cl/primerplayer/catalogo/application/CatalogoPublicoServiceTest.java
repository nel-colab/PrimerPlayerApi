package cl.primerplayer.catalogo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import cl.primerplayer.catalogo.api.ConsultarCatalogoPublicoUseCase.FiltroCatalogo;
import cl.primerplayer.catalogo.api.ConsultarCatalogoPublicoUseCase.OrdenCatalogo;
import cl.primerplayer.catalogo.api.ConsultarCatalogoPublicoUseCase.ResultadoCatalogo;
import cl.primerplayer.catalogo.api.ProductoPublico;
import cl.primerplayer.catalogo.application.port.out.CatalogoPublicoRepositorio;
import cl.primerplayer.shared.api.ExcepcionNegocio;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import cl.primerplayer.shared.api.ValidacionException;

class CatalogoPublicoServiceTest {

    /** Repositorio en memoria: solo conoce los productos "visibles". */
    static class RepoFalso implements CatalogoPublicoRepositorio {
        final List<ProductoPublico> visibles = new ArrayList<>();
        int ultimoLimite;

        @Override public ResultadoCatalogo buscar(FiltroCatalogo filtro) { throw new UnsupportedOperationException(); }

        @Override public List<ProductoPublico> masPopulares(int limite) {
            ultimoLimite = limite;
            return visibles.stream().limit(limite).toList();
        }

        @Override public Optional<ProductoPublico> visible(long id) {
            return visibles.stream().filter(p -> p.id() == id).findFirst();
        }

        @Override public List<ProductoPublico> visibles(java.util.Collection<Long> ids) {
            return visibles.stream().filter(p -> ids.contains(p.id())).toList();
        }

        @Override public List<ProductoPublico> relacionados(long id, int limite) {
            ultimoLimite = limite;
            return visibles.stream().filter(p -> p.id() != id).limit(limite).toList();
        }
    }

    final RepoFalso repo = new RepoFalso();
    final CatalogoPublicoService service = new CatalogoPublicoService(repo);

    static ProductoPublico producto(long id) {
        return new ProductoPublico(id, "Producto " + id, null, 1990, "tcg", "sobres", null, null, null, null,
                null, null, 3, 0, LocalDate.of(2026, 11, 1), List.of());
    }

    // ---------- detalle y relacionados ----------

    @Test
    void obtener_visible() {
        repo.visibles.add(producto(1));
        assertThat(service.obtener(1).id()).isEqualTo(1);
    }

    @Test
    void obtener_noVisible_404ItemUnavailable() {
        assertThatThrownBy(() -> service.obtener(99))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .extracting(e -> ((ExcepcionNegocio) e).code()).isEqualTo("item-unavailable");
    }

    @Test
    void relacionados_deUnProductoNoVisible_404() {
        assertThatThrownBy(() -> service.relacionados(99, 4)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void relacionados_excluyeElPropioProducto() {
        repo.visibles.addAll(List.of(producto(1), producto(2), producto(3)));
        assertThat(service.relacionados(1, 4)).extracting(ProductoPublico::id).containsExactly(2L, 3L);
    }

    // ---------- límites ----------

    @Test
    void destacados_limiteFueraDeRango_validation() {
        assertThatThrownBy(() -> service.destacados(0)).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> service.destacados(25)).isInstanceOf(ValidacionException.class);
        service.destacados(24);
        assertThat(repo.ultimoLimite).isEqualTo(24);
    }

    @Test
    void relacionados_limiteFueraDeRango_validation() {
        repo.visibles.add(producto(1));
        assertThatThrownBy(() -> service.relacionados(1, 0)).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> service.relacionados(1, 13)).isInstanceOf(ValidacionException.class);
    }

    // ---------- filtro ----------

    static FiltroCatalogo filtro(Long min, Long max, int page, int size) {
        return new FiltroCatalogo(null, null, false, min, max, null, null, null, null, null, null, null, page, size);
    }

    @Test
    void filtro_normalizaVaciosYOrdenPorDefecto() {
        var f = new FiltroCatalogo("  ", "  charizard ", false, null, null, Set.of("sobres", " "), null, null, null,
                null, null, null, 0, 24);
        assertThat(f.categoria()).isNull();
        assertThat(f.q()).isEqualTo("charizard");
        assertThat(f.tipo()).containsExactly("sobres");
        assertThat(f.idioma()).isEmpty();
        assertThat(f.orden()).isEqualTo(OrdenCatalogo.RECOMENDADOS);
    }

    @Test
    void filtro_rangoDePrecioInvertido_validation() {
        assertThatThrownBy(() -> filtro(5000L, 1000L, 0, 24)).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> filtro(-1L, null, 0, 24)).isInstanceOf(ValidacionException.class);
    }

    @Test
    void filtro_paginacionFueraDeRango_validation() {
        assertThatThrownBy(() -> filtro(null, null, -1, 24)).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> filtro(null, null, 0, 0)).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> filtro(null, null, 0, 101)).isInstanceOf(ValidacionException.class);
    }

    @Test
    void orden_soloListaBlanca() {
        assertThat(OrdenCatalogo.deParametro("precio-asc")).isEqualTo(OrdenCatalogo.PRECIO_ASC);
        assertThat(OrdenCatalogo.deParametro(null)).isEqualTo(OrdenCatalogo.RECOMENDADOS);
        assertThatThrownBy(() -> OrdenCatalogo.deParametro("stock,desc")).isInstanceOf(ValidacionException.class);
    }
}
