package cl.primerplayer.catalogo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import cl.primerplayer.catalogo.api.CodigoBarrasInmutableException;
import cl.primerplayer.catalogo.api.DatosProducto;
import cl.primerplayer.catalogo.api.DatosProducto.ImagenProducto;
import cl.primerplayer.catalogo.api.EstadoAprobacion;
import cl.primerplayer.catalogo.api.ProductoNoAprobadoException;
import cl.primerplayer.shared.api.LimiteSoloAdminException;
import cl.primerplayer.shared.api.Rol;
import cl.primerplayer.shared.api.ValidacionException;

class ProductoTest {

    static final Instant AHORA = Instant.parse("2026-11-11T15:00:00Z");
    static final String EAN = "7801234567894";

    static DatosProducto datos(String codigo, Integer limite, List<ImagenProducto> imagenes) {
        return new DatosProducto(" Sobre Black Bolt ", "", 4990, "tcg", "Sobre", "Inglés", null, null,
                "Pokémon", null, codigo, limite, false, imagenes);
    }

    static Producto existente(String codigo, Integer limite) {
        Producto p = Producto.crear(datos(codigo, limite, List.of()), LocalDate.of(2026, 11, 11), AHORA, false);
        return Producto.reconstituir(1L, p.datos(), 5, 0, p.fechaIngreso(), 3, AHORA, null,
                EstadoAprobacion.APROBADO, false);
    }

    // ---------- código de barras ----------

    @ParameterizedTest
    @ValueSource(strings = {"7801234567894", "96385074", "036000291452"})
    void codigosGtinValidos(String codigo) {
        assertThat(CodigoBarras.esValido(codigo)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"7801234567891", "96385075", "12345", "78012345678ab", "12345678901234"})
    void codigosInvalidos(String codigo) {
        assertThat(CodigoBarras.esValido(codigo)).isFalse();
    }

    @Test
    void eanInterno_esUnEan13ValidoConPrefijo200() {
        assertThat(CodigoBarras.eanInterno(1)).isEqualTo("2000000000015");
        assertThat(CodigoBarras.eanInterno(1000)).startsWith("200000001000").hasSize(13);
        for (long n : new long[] {1, 7, 1000, 123_456_789, 999_999_999}) {
            assertThat(CodigoBarras.esValido(CodigoBarras.eanInterno(n))).as("correlativo " + n).isTrue();
        }
        assertThatThrownBy(() -> CodigoBarras.eanInterno(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CodigoBarras.eanInterno(1_000_000_000L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void crear_normalizaYValida() {
        Producto p = Producto.crear(datos("", null, List.of()), LocalDate.of(2026, 11, 11), AHORA, false);
        assertThat(p.datos().nombre()).isEqualTo("Sobre Black Bolt");
        assertThat(p.datos().codigoBarras()).isNull();
        assertThat(p.datos().descripcion()).isNull();
        assertThat(p.stock()).isZero();
    }

    @Test
    void crear_conCodigoConVerificadorMalo_validation() {
        assertThatThrownBy(() -> Producto.crear(datos("7801234567891", null, List.of()), LocalDate.now(), AHORA, false))
                .isInstanceOf(ValidacionException.class);
    }

    @Test
    void vendedor_noPuedeAgregarNiCambiarNiQuitarElCodigo() {
        assertThatThrownBy(() -> existente(null, null).actualizar(datos(EAN, null, List.of()), Rol.VENDEDOR, AHORA))
                .isInstanceOf(CodigoBarrasInmutableException.class)
                .extracting(e -> ((CodigoBarrasInmutableException) e).code()).isEqualTo("barcode-immutable");
        Producto conCodigo = existente(EAN, null);
        assertThatThrownBy(() -> conCodigo.actualizar(datos("96385074", null, List.of()), Rol.VENDEDOR, AHORA))
                .isInstanceOf(CodigoBarrasInmutableException.class);
        assertThatThrownBy(() -> conCodigo.actualizar(datos(null, null, List.of()), Rol.VENDEDOR, AHORA))
                .isInstanceOf(CodigoBarrasInmutableException.class);
    }

    @Test
    void vendedor_puedeEditarElRestoSiNoTocaElCodigo() {
        assertThat(existente(EAN, null).actualizar(datos(EAN, null, List.of()), Rol.VENDEDOR, AHORA)
                .datos().codigoBarras()).isEqualTo(EAN);
        assertThat(existente(null, null).actualizar(datos("", null, List.of()), Rol.VENDEDOR, AHORA)
                .datos().codigoBarras()).isNull();
    }

    @Test
    void admin_puedeAgregarYCambiarElCodigo() {
        assertThat(existente(null, null).actualizar(datos(EAN, null, List.of()), Rol.ADMIN, AHORA)
                .datos().codigoBarras()).isEqualTo(EAN);
        assertThat(existente(EAN, null).actualizar(datos("96385074", null, List.of()), Rol.ADMIN, AHORA)
                .datos().codigoBarras()).isEqualTo("96385074");
    }

    // ---------- límite por cliente ----------

    @Test
    void limiteYaConfigurado_soloAdminLoCambia() {
        Producto p = existente(null, 4);
        assertThatThrownBy(() -> p.actualizar(datos(null, 2, List.of()), Rol.VENDEDOR, AHORA))
                .isInstanceOf(LimiteSoloAdminException.class)
                .extracting(e -> ((LimiteSoloAdminException) e).code()).isEqualTo("limit-admin-only");
        assertThatThrownBy(() -> p.actualizar(datos(null, null, List.of()), Rol.VENDEDOR, AHORA))
                .isInstanceOf(LimiteSoloAdminException.class);
        assertThat(p.actualizar(datos(null, 2, List.of()), Rol.ADMIN, AHORA).datos().limitePorCliente()).isEqualTo(2);
        assertThat(p.actualizar(datos(null, 4, List.of()), Rol.VENDEDOR, AHORA).datos().limitePorCliente()).isEqualTo(4);
    }

    @Test
    void limiteVacio_cualquierRolInternoLoDefine() {
        assertThat(existente(null, null).actualizar(datos(null, 3, List.of()), Rol.VENDEDOR, AHORA)
                .datos().limitePorCliente()).isEqualTo(3);
    }

    // ---------- imágenes ----------

    @Test
    void imagenes_exactamenteUnaPortadaMaximoCincoYSoloHttps() {
        var ok = new ImagenProducto("https://cdn.test/a.webp", true);
        var otra = new ImagenProducto("https://cdn.test/b.webp", false);
        Producto.crear(datos(null, null, List.of(ok, otra)), LocalDate.now(), AHORA, false);

        assertThatThrownBy(() -> Producto.crear(datos(null, null, List.of(otra)), LocalDate.now(), AHORA, false))
                .isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> Producto.crear(datos(null, null, List.of(ok, ok)), LocalDate.now(), AHORA, false))
                .isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> Producto.crear(datos(null, null,
                List.of(ok, otra, otra, otra, otra, otra)), LocalDate.now(), AHORA, false))
                .isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> Producto.crear(datos(null, null,
                List.of(new ImagenProducto("http://cdn.test/a.webp", true))), LocalDate.now(), AHORA, false))
                .isInstanceOf(ValidacionException.class);
    }

    // ---------- aprobación ----------

    static DatosProducto publicado() {
        return new DatosProducto("Sobre", null, 4990, "tcg", null, null, null, null, null, null, null, null,
                true, List.of());
    }

    @Test
    void crear_porVendedor_nacePendienteYSinPublicarAunquePidaPublicar() {
        Producto p = Producto.crear(publicado(), LocalDate.of(2026, 11, 11), AHORA, true);
        assertThat(p.aprobacion()).isEqualTo(EstadoAprobacion.PENDIENTE);
        assertThat(p.datos().publicado()).isFalse();
    }

    @Test
    void crear_porAdmin_naceAprobadoYComoLoPidio() {
        Producto p = Producto.crear(publicado(), LocalDate.of(2026, 11, 11), AHORA, false);
        assertThat(p.aprobacion()).isEqualTo(EstadoAprobacion.APROBADO);
        assertThat(p.datos().publicado()).isTrue();
    }

    @Test
    void publicarSinAprobar_productNotApproved_yAprobarLoPermite() {
        Producto pendiente = Producto.crear(publicado(), LocalDate.of(2026, 11, 11), AHORA, true);
        assertThatThrownBy(() -> pendiente.conPublicacion(true, AHORA))
                .isInstanceOf(ProductoNoAprobadoException.class);

        Producto aprobado = pendiente.aprobar(AHORA).conPublicacion(true, AHORA);
        assertThat(aprobado.aprobacion()).isEqualTo(EstadoAprobacion.APROBADO);
        assertThat(aprobado.datos().publicado()).isTrue();
    }

    @Test
    void rechazar_dejaSinPublicar_yReenviarVuelveAPendiente() {
        Producto pendiente = Producto.crear(publicado(), LocalDate.of(2026, 11, 11), AHORA, true);
        Producto rechazado = pendiente.rechazar(AHORA);
        assertThat(rechazado.aprobacion()).isEqualTo(EstadoAprobacion.RECHAZADO);
        assertThat(rechazado.reenviarARevision(AHORA).aprobacion()).isEqualTo(EstadoAprobacion.PENDIENTE);
    }

    @Test
    void validarPropuesta_aplicaLasReglasDelVendedorSinCambiarElProducto() {
        Producto p = existente(EAN, 2);
        assertThatThrownBy(() -> p.validarPropuesta(datos(null, 2, List.of()), Rol.VENDEDOR))
                .isInstanceOf(CodigoBarrasInmutableException.class);
        assertThatThrownBy(() -> p.validarPropuesta(datos(EAN, 9, List.of()), Rol.VENDEDOR))
                .isInstanceOf(LimiteSoloAdminException.class);

        DatosProducto propuesto = p.validarPropuesta(datos(EAN, 2, List.of()), Rol.VENDEDOR);
        assertThat(propuesto.nombre()).isEqualTo("Sobre Black Bolt");   // normalizado
        assertThat(p.datos().descripcion()).isNull();                   // el producto no cambia
    }
}
