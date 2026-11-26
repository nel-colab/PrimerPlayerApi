package cl.primerplayer.catalogo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import cl.primerplayer.catalogo.api.AdministrarMaestrosUseCase.CategoriaAdmin;
import cl.primerplayer.catalogo.api.AdministrarMaestrosUseCase.FranquiciaAdmin;
import cl.primerplayer.catalogo.api.CategoriaDuplicadaException;
import cl.primerplayer.catalogo.api.CategoriaEnUsoException;
import cl.primerplayer.catalogo.api.FranquiciaDuplicadaException;
import cl.primerplayer.catalogo.api.FranquiciaEnUsoException;
import cl.primerplayer.catalogo.application.port.out.MaestrosAdminRepositorio;
import cl.primerplayer.catalogo.application.port.out.MaestrosAdminRepositorio.FilaCategoria;
import cl.primerplayer.catalogo.application.port.out.MaestrosAdminRepositorio.FilaFranquicia;
import cl.primerplayer.catalogo.events.MaestroCatalogoCambiado;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import cl.primerplayer.shared.api.ValidacionException;
import tools.jackson.databind.json.JsonMapper;

class MaestrosAdminServiceTest {

    static final Instant AHORA = Instant.parse("2026-11-26T15:00:00Z");
    static final long ADMIN = 1;

    final MaestrosAdminRepositorio repo = mock(MaestrosAdminRepositorio.class);
    final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    final MaestrosAdminService service = new MaestrosAdminService(repo, eventos, Clock.fixed(AHORA, ZoneOffset.UTC),
            JsonMapper.builder().build());

    static FilaCategoria cat(int id, String slug, String titulo, long productos, long revisiones) {
        return new FilaCategoria(id, slug, titulo, 50, productos, revisiones);
    }

    static FilaFranquicia fr(int id, String nombre, long productos, long revisiones) {
        return new FilaFranquicia(id, nombre, productos, revisiones);
    }

    MaestroCatalogoCambiado evento() {
        ArgumentCaptor<MaestroCatalogoCambiado> ev = ArgumentCaptor.forClass(MaestroCatalogoCambiado.class);
        verify(eventos).publishEvent(ev.capture());
        return ev.getValue();
    }

    // ---------- slug ----------

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Juegos de Mesa & Más|juegos-de-mesa-mas",
            "  Ñandú   Pokémon  |nandu-pokemon",
            "--¡Hola!--|hola",
            "Rol 2026|rol-2026",
    })
    void slugDesdeElTitulo(String titulo, String slug) {
        assertThat(MaestrosAdminService.slugDe(titulo)).isEqualTo(slug);
    }

    @Test
    void slugLargoSeCortaSinGuionFinal() {
        String s = MaestrosAdminService.slugDe("a".repeat(59) + " b c d");
        assertThat(s).hasSizeLessThanOrEqualTo(60).doesNotEndWith("-").startsWith("a".repeat(59));
    }

    // ---------- categorías ----------

    @Test
    void listaCategoriasConUso() {
        when(repo.categorias()).thenReturn(List.of(cat(1, "tcg", "TCG", 3, 0), cat(2, "nueva", "Nueva", 0, 0),
                cat(3, "propuesta", "Propuesta", 0, 2)));

        assertThat(service.categorias()).extracting(CategoriaAdmin::enUso).containsExactly(true, false, true);
    }

    @Test
    void crearCategoriaConSlugGeneradoYOrdenAlFinal() {
        when(repo.siguienteOrden()).thenReturn(60);
        when(repo.crearCategoria("juegos-de-mesa", "Juegos de mesa", 60)).thenReturn(9);
        when(repo.categoria(9)).thenReturn(Optional.of(new FilaCategoria(9, "juegos-de-mesa", "Juegos de mesa", 60, 0, 0)));

        CategoriaAdmin c = service.crearCategoria(ADMIN, " Juegos  de mesa ", null, null);

        assertThat(c.slug()).isEqualTo("juegos-de-mesa");
        MaestroCatalogoCambiado e = evento();
        assertThat(e.accion()).isEqualTo("CATEGORIA_CREADA");
        assertThat(e.entidad()).isEqualTo("categoria");
        assertThat(e.antes()).isNull();
        assertThat(e.despues()).contains("\"slug\":\"juegos-de-mesa\"").contains("\"orden\":60");
        assertThat(e.ocurridoEn()).isEqualTo(AHORA);
    }

    @Test
    void crearCategoriaConSlugYOrdenDados() {
        when(repo.crearCategoria("comics", "Cómics", 5)).thenReturn(4);
        when(repo.categoria(4)).thenReturn(Optional.of(cat(4, "comics", "Cómics", 0, 0)));

        service.crearCategoria(ADMIN, "Cómics", " COMICS ", 5);

        verify(repo, never()).siguienteOrden();
        verify(repo).existeCategoria("comics", "Cómics", null);
    }

    @Test
    void crearCategoriaInvalida() {
        assertThatThrownBy(() -> service.crearCategoria(ADMIN, null, null, null)).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> service.crearCategoria(ADMIN, "x".repeat(81), null, null)).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> service.crearCategoria(ADMIN, "¡¡¡", null, null)).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> service.crearCategoria(ADMIN, "Todos", null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("reservado");
        assertThatThrownBy(() -> service.crearCategoria(ADMIN, "X", "con espacio", null)).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> service.crearCategoria(ADMIN, "X", "y".repeat(61), null)).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> service.crearCategoria(ADMIN, "X", null, -1)).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> service.crearCategoria(ADMIN, "X", null, 10_000)).isInstanceOf(ValidacionException.class);
        verify(repo, never()).crearCategoria(anyString(), anyString(), anyInt());
    }

    @Test
    void crearCategoriaDuplicada() {
        when(repo.existeCategoria("tcg", "TCG", null)).thenReturn(true);
        assertThatThrownBy(() -> service.crearCategoria(ADMIN, "TCG", null, 1)).isInstanceOf(CategoriaDuplicadaException.class);

        when(repo.crearCategoria(anyString(), anyString(), anyInt())).thenThrow(new DuplicateKeyException("uq"));
        assertThatThrownBy(() -> service.crearCategoria(ADMIN, "Otra", null, 1)).isInstanceOf(CategoriaDuplicadaException.class);
        verifyNoInteractions(eventos);
    }

    @Test
    void actualizarCategoriaMantieneOrdenSiNoViene() {
        when(repo.categoria(4)).thenReturn(Optional.of(cat(4, "comics", "Comics", 0, 0)),
                Optional.of(new FilaCategoria(4, "comics", "Cómics", 50, 0, 0)));

        CategoriaAdmin c = service.actualizarCategoria(ADMIN, 4, "Cómics", null);

        verify(repo).actualizarCategoria(4, "Cómics", 50);
        assertThat(c.titulo()).isEqualTo("Cómics");
        MaestroCatalogoCambiado e = evento();
        assertThat(e.antes()).contains("\"titulo\":\"Comics\"");
        assertThat(e.despues()).contains("\"titulo\":\"Cómics\"");
    }

    @Test
    void actualizarCategoriaConOrdenNuevo() {
        when(repo.categoria(4)).thenReturn(Optional.of(cat(4, "comics", "Comics", 0, 0)));
        service.actualizarCategoria(ADMIN, 4, "Comics", 3);
        verify(repo).actualizarCategoria(4, "Comics", 3);
    }

    @Test
    void actualizarCategoriaDuplicadaOInexistente() {
        when(repo.categoria(4)).thenReturn(Optional.of(cat(4, "comics", "Comics", 0, 0)));
        when(repo.existeCategoria("comics", "TCG", 4)).thenReturn(true);
        assertThatThrownBy(() -> service.actualizarCategoria(ADMIN, 4, "TCG", null))
                .isInstanceOf(CategoriaDuplicadaException.class);

        when(repo.categoria(99)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.actualizarCategoria(ADMIN, 99, "X", null))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void eliminarCategoriaSinUso() {
        when(repo.categoria(4)).thenReturn(Optional.of(cat(4, "comics", "Comics", 0, 0)));

        service.eliminarCategoria(ADMIN, 4);

        verify(repo).eliminarCategoria(4);
        MaestroCatalogoCambiado e = evento();
        assertThat(e.accion()).isEqualTo("CATEGORIA_ELIMINADA");
        assertThat(e.despues()).isNull();
    }

    @Test
    void categoriaConProductosORevisionesNoSeElimina() {
        when(repo.categoria(1)).thenReturn(Optional.of(cat(1, "tcg", "TCG", 5, 0)));
        when(repo.categoria(2)).thenReturn(Optional.of(cat(2, "propuesta", "Propuesta", 0, 1)));

        assertThatThrownBy(() -> service.eliminarCategoria(ADMIN, 1)).isInstanceOf(CategoriaEnUsoException.class);
        assertThatThrownBy(() -> service.eliminarCategoria(ADMIN, 2)).isInstanceOf(CategoriaEnUsoException.class);
        verify(repo, never()).eliminarCategoria(anyInt());
    }

    @Test
    void categoriaTomadaPorUnProductoEnElUltimoMomento() {
        when(repo.categoria(4)).thenReturn(Optional.of(cat(4, "comics", "Comics", 0, 0)));
        doThrow(new DataIntegrityViolationException("fk")).when(repo).eliminarCategoria(4);

        assertThatThrownBy(() -> service.eliminarCategoria(ADMIN, 4)).isInstanceOf(CategoriaEnUsoException.class);
        verifyNoInteractions(eventos);
    }

    // ---------- franquicias ----------

    @Test
    void listaFranquicias() {
        when(repo.franquicias()).thenReturn(List.of(fr(1, "Pokémon", 10, 0), fr(2, "Digimon", 0, 0)));
        assertThat(service.franquicias()).extracting(FranquiciaAdmin::enUso).containsExactly(true, false);
    }

    @Test
    void crearFranquicia() {
        when(repo.crearFranquicia("Digimon")).thenReturn(7);
        when(repo.franquicia(7)).thenReturn(Optional.of(fr(7, "Digimon", 0, 0)));

        FranquiciaAdmin f = service.crearFranquicia(ADMIN, "  Digimon ");

        assertThat(f.nombre()).isEqualTo("Digimon");
        assertThat(evento().despues()).isEqualTo("{\"nombre\":\"Digimon\"}");
    }

    @Test
    void crearFranquiciaInvalidaODuplicada() {
        assertThatThrownBy(() -> service.crearFranquicia(ADMIN, null)).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> service.crearFranquicia(ADMIN, "x".repeat(61))).isInstanceOf(ValidacionException.class);

        when(repo.existeFranquicia("Pokémon", null)).thenReturn(true);
        assertThatThrownBy(() -> service.crearFranquicia(ADMIN, "Pokémon")).isInstanceOf(FranquiciaDuplicadaException.class);

        when(repo.crearFranquicia("Nueva")).thenThrow(new DuplicateKeyException("uq"));
        assertThatThrownBy(() -> service.crearFranquicia(ADMIN, "Nueva")).isInstanceOf(FranquiciaDuplicadaException.class);
    }

    @Test
    void renombrarFranquiciaActualizaRevisionesPendientes() {
        when(repo.franquicia(7)).thenReturn(Optional.of(fr(7, "Digimon", 0, 1)), Optional.of(fr(7, "Digimon TCG", 0, 1)));

        FranquiciaAdmin f = service.renombrarFranquicia(ADMIN, 7, "Digimon TCG");

        verify(repo).renombrarFranquicia(7, "Digimon TCG");
        verify(repo).renombrarFranquiciaEnRevisionesPendientes("Digimon", "Digimon TCG");
        assertThat(f.nombre()).isEqualTo("Digimon TCG");
        MaestroCatalogoCambiado e = evento();
        assertThat(e.antes()).isEqualTo("{\"nombre\":\"Digimon\"}");
        assertThat(e.accion()).isEqualTo("FRANQUICIA_EDITADA");
    }

    @Test
    void renombrarAlMismoNombreNoHaceNada() {
        when(repo.franquicia(7)).thenReturn(Optional.of(fr(7, "Digimon", 0, 0)));

        service.renombrarFranquicia(ADMIN, 7, " Digimon ");

        verify(repo, never()).renombrarFranquicia(anyInt(), anyString());
        verifyNoInteractions(eventos);
    }

    @Test
    void renombrarAUnNombreQueYaExiste() {
        when(repo.franquicia(7)).thenReturn(Optional.of(fr(7, "Digimon", 0, 0)));
        when(repo.existeFranquicia("Pokémon", 7)).thenReturn(true);
        assertThatThrownBy(() -> service.renombrarFranquicia(ADMIN, 7, "Pokémon"))
                .isInstanceOf(FranquiciaDuplicadaException.class);

        doThrow(new DuplicateKeyException("uq")).when(repo).renombrarFranquicia(eq(7), eq("Otra"));
        assertThatThrownBy(() -> service.renombrarFranquicia(ADMIN, 7, "Otra"))
                .isInstanceOf(FranquiciaDuplicadaException.class);
        verify(repo, never()).renombrarFranquiciaEnRevisionesPendientes(any(), any());
    }

    @Test
    void eliminarFranquicia() {
        when(repo.franquicia(7)).thenReturn(Optional.of(fr(7, "Digimon", 0, 0)));

        service.eliminarFranquicia(ADMIN, 7);

        verify(repo).eliminarFranquicia(7);
        assertThat(evento().accion()).isEqualTo("FRANQUICIA_ELIMINADA");
    }

    @Test
    void franquiciaEnUsoNoSeElimina() {
        when(repo.franquicia(1)).thenReturn(Optional.of(fr(1, "Pokémon", 3, 0)));
        when(repo.franquicia(2)).thenReturn(Optional.of(fr(2, "Propuesta", 0, 1)));
        when(repo.franquicia(3)).thenReturn(Optional.of(fr(3, "Carrera", 0, 0)));
        doThrow(new DataIntegrityViolationException("fk")).when(repo).eliminarFranquicia(3);

        assertThatThrownBy(() -> service.eliminarFranquicia(ADMIN, 1)).isInstanceOf(FranquiciaEnUsoException.class);
        assertThatThrownBy(() -> service.eliminarFranquicia(ADMIN, 2)).isInstanceOf(FranquiciaEnUsoException.class);
        assertThatThrownBy(() -> service.eliminarFranquicia(ADMIN, 3)).isInstanceOf(FranquiciaEnUsoException.class);
        verifyNoInteractions(eventos);
    }

    @Test
    void franquiciaInexistente() {
        when(repo.franquicia(99)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.eliminarFranquicia(ADMIN, 99)).isInstanceOf(RecursoNoEncontradoException.class);
        verify(repo, never()).existeFranquicia(any(), isNull());
    }
}
