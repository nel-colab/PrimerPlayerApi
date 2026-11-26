package cl.primerplayer.catalogo.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import cl.primerplayer.catalogo.api.DatosProducto;
import cl.primerplayer.catalogo.api.DatosProducto.ImagenProducto;
import cl.primerplayer.catalogo.api.EstadoAprobacion;
import cl.primerplayer.catalogo.api.ProductoAdmin;
import cl.primerplayer.catalogo.api.ProductoPublico;
import cl.primerplayer.catalogo.api.RevisionesProductoUseCase.EstadoRevision;
import cl.primerplayer.catalogo.api.RevisionesProductoUseCase.RevisionProducto;
import cl.primerplayer.catalogo.api.RevisionesProductoUseCase.TipoRevision;
import cl.primerplayer.catalogo.web.dto.ImagenDto;
import cl.primerplayer.catalogo.web.dto.ProductoRequest;

class CatalogoWebMapperTest {

    final CatalogoWebMapper mapper = new CatalogoWebMapperImpl();

    static final UUID ARCHIVO = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final ImagenProducto PORTADA = new ImagenProducto("https://cdn/p.webp", true, ARCHIVO);
    static final ImagenProducto OTRA = new ImagenProducto("https://cdn/o.webp", false);
    static final Instant AHORA = Instant.parse("2026-11-26T15:00:00Z");

    static ProductoAdmin actual(List<ImagenProducto> imagenes) {
        return new ProductoAdmin(1, "Sobre", "desc", 4990, "tcg", "Sobre", "Inglés", "TPC", null, "Pokémon",
                "SV", "0820650858306", 4, 10, 3, LocalDate.of(2026, 11, 1), true, imagenes,
                EstadoAprobacion.APROBADO, false, 2, AHORA);
    }

    static DatosProducto igual(List<ImagenProducto> imagenes) {
        return new DatosProducto("Sobre", "desc", 4990, "tcg", "Sobre", "Inglés", "TPC", null, "Pokémon", "SV",
                "0820650858306", 4, true, imagenes);
    }

    @Test
    void requestADatos() {
        var req = new ProductoRequest("Sobre", null, 4990L, "tcg", null, null, null, null, null, null, null, 3, null,
                List.of(new ImagenDto("https://cdn/p.webp", true, null)));

        DatosProducto d = mapper.toDatos(req);

        assertThat(d.limitePorCliente()).isEqualTo(3);
        assertThat(d.publicado()).isFalse();
        assertThat(d.imagenes()).containsExactly(new ImagenProducto("https://cdn/p.webp", true, null));
    }

    @Test
    void requestSinPrecioNiImagenesYPublicado() {
        var req = new ProductoRequest("Sobre", null, null, "tcg", null, null, null, null, null, null, null, null, true, null);

        DatosProducto d = mapper.toDatos(req);

        assertThat(d.precio()).isZero();
        assertThat(d.publicado()).isTrue();
        assertThat(d.imagenes()).isEmpty();
    }

    @Test
    void nulosDevuelvenNulo() {
        assertThat(mapper.toDatos(null)).isNull();
        assertThat(mapper.toResponse(null)).isNull();
        assertThat(mapper.toPublico(null)).isNull();
        assertThat(mapper.toImagen(null)).isNull();
        assertThat(mapper.toDto(null)).isNull();
        assertThat(mapper.toPropuesta(null)).isNull();
    }

    @Test
    void responseAdminConPortada() {
        var r = mapper.toResponse(actual(List.of(OTRA, PORTADA)));

        assertThat(r.imagen()).isEqualTo("https://cdn/p.webp");
        assertThat(r.limitePorUsuario()).isEqualTo(4);
        assertThat(r.fecha()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void publicoOrdenaPortadaPrimeroYSinImagenesNoTienePortada() {
        var conImagenes = new ProductoPublico(1, "Sobre", null, 4990, "tcg", null, null, null, null, null, null, null,
                3, 0, LocalDate.of(2026, 11, 1), List.of(OTRA, PORTADA));
        var sinImagenes = new ProductoPublico(2, "Otro", null, 1, "tcg", null, null, null, null, null, null, 1,
                1, 0, LocalDate.of(2026, 11, 1), null);

        assertThat(mapper.toPublico(conImagenes).imagenes()).containsExactly("https://cdn/p.webp", "https://cdn/o.webp");
        assertThat(mapper.toPublico(sinImagenes).imagen()).isNull();
        assertThat(mapper.toPublico(sinImagenes).imagenes()).isEmpty();
    }

    @Test
    void camposModificadosSinCambiosEsVacio() {
        assertThat(mapper.camposModificados(actual(List.of(PORTADA)), igual(List.of(PORTADA)))).isEmpty();
    }

    @Test
    void camposModificadosDetectaCadaCampo() {
        var p = new DatosProducto("Sobre 2", "otra", 5990, "figuras", "Caja", "Español", "Bandai", "PS5", "One Piece",
                "OP", "12345678", null, false, List.of(OTRA));

        assertThat(mapper.camposModificados(actual(List.of(PORTADA)), p)).containsExactly(
                "nombre", "descripcion", "precio", "categoria", "tipo", "idioma", "marca", "plataforma", "franquicia",
                "linea", "codigoBarras", "limitePorUsuario", "publicado", "imagenes");
    }

    @Test
    void camposModificadosDistingueArchivoDeUrlYPortada() {
        var mismaUrlComoArchivo = List.of(new ImagenProducto("https://cdn/p.webp", true, null));
        var mismaPeroSinPortada = List.of(new ImagenProducto("https://cdn/p.webp", false, ARCHIVO));

        assertThat(mapper.camposModificados(actual(List.of(PORTADA)), igual(mismaUrlComoArchivo))).containsExactly("imagenes");
        assertThat(mapper.camposModificados(actual(List.of(PORTADA)), igual(mismaPeroSinPortada))).containsExactly("imagenes");
    }

    @Test
    void revisionDeEdicionTraeActualPropuestaYCampos() {
        var rev = new RevisionProducto(9, 1, TipoRevision.EDICION, EstadoRevision.APROBADA, actual(List.of(PORTADA)),
                new DatosProducto("Sobre 2", "desc", 4990, "tcg", "Sobre", "Inglés", "TPC", null, "Pokémon", "SV",
                        "0820650858306", 4, true, List.of(PORTADA)),
                igual(List.of(PORTADA)), false, null, 7, AHORA, 3L, AHORA);

        var r = mapper.toRevision(rev);

        assertThat(r.actual().id()).isEqualTo(1);
        assertThat(r.propuesto().nombre()).isEqualTo("Sobre 2");
        assertThat(r.propuesto().imagen()).isEqualTo("https://cdn/p.webp");
        assertThat(r.aprobado()).isNotNull();
        assertThat(r.camposModificados()).containsExactly("nombre");
    }

    @Test
    void revisionDeAltaSinActualNiPropuesta() {
        var rev = new RevisionProducto(9, 1, TipoRevision.ALTA, EstadoRevision.PENDIENTE, null, null, null, false,
                null, 7, AHORA, null, null);

        var r = mapper.toRevision(rev);

        assertThat(r.actual()).isNull();
        assertThat(r.propuesto()).isNull();
        assertThat(r.camposModificados()).isEmpty();
    }

    @Test
    void revisionConPropuestaPeroSinActual() {
        var rev = new RevisionProducto(9, 1, TipoRevision.ALTA, EstadoRevision.PENDIENTE, null, igual(List.of()),
                null, false, null, 7, AHORA, null, null);

        assertThat(mapper.toRevision(rev).camposModificados()).isEmpty();
    }
}
