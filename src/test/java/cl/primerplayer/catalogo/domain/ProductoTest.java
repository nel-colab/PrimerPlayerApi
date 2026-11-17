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
import cl.primerplayer.shared.api.LimiteSoloAdminException;
import cl.primerplayer.shared.api.Rol;
import cl.primerplayer.shared.api.ValidacionException;

class ProductoTest {

    static final Instant AHORA = Instant.parse("2026-11-11T15:00:00Z");
    static final String EAN = "7801234567894";

    static DatosProducto datos(String codigo, Integer limite, List<ImagenProducto> imagenes) {
        return new DatosProducto(" Sobre Black Bolt ", "", 4990, "tcg-pokemon", "Sobre", "Inglés", null, null,
                "Pokémon", null, codigo, limite, false, imagenes);
    }

    static Producto existente(String codigo, Integer limite) {
        Producto p = Producto.crear(datos(codigo, limite, List.of()), LocalDate.of(2026, 11, 11), AHORA);
        return Producto.reconstituir(1L, p.datos(), 5, 0, p.fechaIngreso(), 3, AHORA, null);
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
    void crear_normalizaYValida() {
        Producto p = Producto.crear(datos("", null, List.of()), LocalDate.of(2026, 11, 11), AHORA);
        assertThat(p.datos().nombre()).isEqualTo("Sobre Black Bolt");
        assertThat(p.datos().codigoBarras()).isNull();
        assertThat(p.datos().descripcion()).isNull();
        assertThat(p.stock()).isZero();
    }

    @Test
    void crear_conCodigoConVerificadorMalo_validation() {
        assertThatThrownBy(() -> Producto.crear(datos("7801234567891", null, List.of()), LocalDate.now(), AHORA))
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
        Producto.crear(datos(null, null, List.of(ok, otra)), LocalDate.now(), AHORA);

        assertThatThrownBy(() -> Producto.crear(datos(null, null, List.of(otra)), LocalDate.now(), AHORA))
                .isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> Producto.crear(datos(null, null, List.of(ok, ok)), LocalDate.now(), AHORA))
                .isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> Producto.crear(datos(null, null,
                List.of(ok, otra, otra, otra, otra, otra)), LocalDate.now(), AHORA))
                .isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> Producto.crear(datos(null, null,
                List.of(new ImagenProducto("http://cdn.test/a.webp", true))), LocalDate.now(), AHORA))
                .isInstanceOf(ValidacionException.class);
    }
}
