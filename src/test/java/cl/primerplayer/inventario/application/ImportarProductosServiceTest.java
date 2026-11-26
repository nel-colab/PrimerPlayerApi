package cl.primerplayer.inventario.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import cl.primerplayer.inventario.api.ImportarProductosUseCase.ErrorFila;
import cl.primerplayer.inventario.api.ImportarProductosUseCase.FilaImportacion;
import cl.primerplayer.inventario.api.ImportarProductosUseCase.ResultadoImportacion;
import cl.primerplayer.shared.api.ExcepcionNegocio;
import cl.primerplayer.shared.api.ValidacionException;

class ImportarProductosServiceTest {

    final ProcesarFilaImportacion procesar = mock(ProcesarFilaImportacion.class);
    final ImportarProductosService service = new ImportarProductosService(procesar);

    /** Fila válida; cada caso cambia un campo. */
    static FilaImportacion fila(UnaryOperator<Campos> ajuste) {
        Campos c = ajuste.apply(new Campos("Sobre Pokémon", 4990L, 5, 2, null, "tcg", null, null, null, null, null, null));
        return new FilaImportacion(c.nombre, null, c.precio, c.stock, c.categoria, c.descripcion, true, c.limite,
                c.franquicia, c.marca, c.tipo, c.plataforma, c.idioma, c.linea);
    }

    record Campos(String nombre, Long precio, Integer stock, Integer limite, String descripcion, String categoria,
                  String tipo, String idioma, String marca, String plataforma, String franquicia, String linea) {
        Campos nombre(String v) { return new Campos(v, precio, stock, limite, descripcion, categoria, tipo, idioma, marca, plataforma, franquicia, linea); }
        Campos precio(Long v) { return new Campos(nombre, v, stock, limite, descripcion, categoria, tipo, idioma, marca, plataforma, franquicia, linea); }
        Campos stock(Integer v) { return new Campos(nombre, precio, v, limite, descripcion, categoria, tipo, idioma, marca, plataforma, franquicia, linea); }
        Campos limite(Integer v) { return new Campos(nombre, precio, stock, v, descripcion, categoria, tipo, idioma, marca, plataforma, franquicia, linea); }
        Campos descripcion(String v) { return new Campos(nombre, precio, stock, limite, v, categoria, tipo, idioma, marca, plataforma, franquicia, linea); }
        Campos categoria(String v) { return new Campos(nombre, precio, stock, limite, descripcion, v, tipo, idioma, marca, plataforma, franquicia, linea); }
        Campos tipo(String v) { return new Campos(nombre, precio, stock, limite, descripcion, categoria, v, idioma, marca, plataforma, franquicia, linea); }
        Campos idioma(String v) { return new Campos(nombre, precio, stock, limite, descripcion, categoria, tipo, v, marca, plataforma, franquicia, linea); }
        Campos marca(String v) { return new Campos(nombre, precio, stock, limite, descripcion, categoria, tipo, idioma, v, plataforma, franquicia, linea); }
        Campos plataforma(String v) { return new Campos(nombre, precio, stock, limite, descripcion, categoria, tipo, idioma, marca, v, franquicia, linea); }
        Campos franquicia(String v) { return new Campos(nombre, precio, stock, limite, descripcion, categoria, tipo, idioma, marca, plataforma, v, linea); }
        Campos linea(String v) { return new Campos(nombre, precio, stock, limite, descripcion, categoria, tipo, idioma, marca, plataforma, franquicia, v); }
    }

    record Caso(String nombre, FilaImportacion fila, String error) {
        @Override public String toString() { return nombre; }
    }

    static List<Caso> invalidas() {
        return List.of(
                new Caso("sin nombre", fila(c -> c.nombre(null)), "El nombre es requerido"),
                new Caso("nombre en blanco", fila(c -> c.nombre("  ")), "El nombre es requerido"),
                new Caso("nombre largo", fila(c -> c.nombre("x".repeat(201))), "exceder 200"),
                new Caso("precio negativo", fila(c -> c.precio(-1L)), "precio no puede ser negativo"),
                new Caso("precio excesivo", fila(c -> c.precio(100_000_001L)), "exceder 100000000"),
                new Caso("stock negativo", fila(c -> c.stock(-1)), "stock no puede ser negativo"),
                new Caso("límite cero", fila(c -> c.limite(0)), "≥ 1"),
                new Caso("límite excesivo", fila(c -> c.limite(1000)), "exceder 999"),
                new Caso("descripción larga", fila(c -> c.descripcion("x".repeat(10_001))), "largo permitido"),
                new Caso("categoría larga", fila(c -> c.categoria("x".repeat(61))), "largo permitido"),
                new Caso("tipo largo", fila(c -> c.tipo("x".repeat(41))), "largo permitido"),
                new Caso("idioma largo", fila(c -> c.idioma("x".repeat(31))), "largo permitido"),
                new Caso("marca larga", fila(c -> c.marca("x".repeat(61))), "largo permitido"),
                new Caso("plataforma larga", fila(c -> c.plataforma("x".repeat(31))), "largo permitido"),
                new Caso("franquicia larga", fila(c -> c.franquicia("x".repeat(61))), "largo permitido"),
                new Caso("línea larga", fila(c -> c.linea("x".repeat(81))), "largo permitido"));
    }

    @ParameterizedTest
    @MethodSource("invalidas")
    void filaInvalidaQuedaComoErrorSinProcesarse(Caso caso) {
        ResultadoImportacion r = service.importar(List.of(caso.fila()), Map.of(), 1);

        assertThat(r.creados()).isZero();
        assertThat(r.errores()).singleElement().satisfies(e -> {
            assertThat(e.fila()).isEqualTo(1);
            assertThat(e.mensaje()).contains(caso.error());
        });
        verify(procesar, never()).procesar(any(), anyLong());
    }

    @Test
    void filaEnLosLimitesEsValida() {
        when(procesar.procesar(any(), anyLong())).thenReturn(ProcesarFilaImportacion.Resultado.CREADO);
        FilaImportacion limite = fila(c -> c.nombre("x".repeat(200)).precio(100_000_000L).stock(0).limite(999)
                .descripcion("d".repeat(10_000)).categoria("c".repeat(60)).tipo("t".repeat(40)).idioma("i".repeat(30))
                .marca("m".repeat(60)).plataforma("p".repeat(30)).franquicia("f".repeat(60)).linea("l".repeat(80)));
        FilaImportacion opcionalesNulos = fila(c -> c.precio(null).stock(null).limite(null));

        ResultadoImportacion r = service.importar(List.of(limite, opcionalesNulos), Map.of(), 1);

        assertThat(r.creados()).isEqualTo(2);
        assertThat(r.errores()).isEmpty();
    }

    @Test
    void cuentaCreadosYActualizadosYJuntaErroresDeLecturaYDeNegocio() {
        FilaImportacion nueva = fila(c -> c.nombre("Nueva"));
        FilaImportacion existente = fila(c -> c.nombre("Existente"));
        FilaImportacion conValidacion = fila(c -> c.nombre("Categoría inexistente"));
        FilaImportacion conNegocio = fila(c -> c.nombre("Código duplicado"));
        FilaImportacion ilegible = fila(c -> c.nombre("Ilegible"));
        when(procesar.procesar(nueva, 9)).thenReturn(ProcesarFilaImportacion.Resultado.CREADO);
        when(procesar.procesar(existente, 9)).thenReturn(ProcesarFilaImportacion.Resultado.ACTUALIZADO);
        when(procesar.procesar(conValidacion, 9)).thenThrow(new ValidacionException("categoria", "Categoría no existe"));
        when(procesar.procesar(conNegocio, 9)).thenThrow(new Duplicado());

        ResultadoImportacion r = service.importar(List.of(nueva, existente, conValidacion, conNegocio, ilegible),
                Map.of(5, "precio no es número"), 9);

        assertThat(r.creados()).isEqualTo(1);
        assertThat(r.actualizados()).isEqualTo(1);
        assertThat(r.errores()).extracting(ErrorFila::fila).containsExactly(3, 4, 5);
        assertThat(r.errores().get(2).mensaje()).isEqualTo("precio no es número");
        verify(procesar, never()).procesar(ilegible, 9);
    }

    @Test
    void erroresDeLecturaNulosSeIgnoran() {
        when(procesar.procesar(any(), anyLong())).thenReturn(ProcesarFilaImportacion.Resultado.ACTUALIZADO);

        ResultadoImportacion r = service.importar(List.of(fila(c -> c)), null, 1);

        assertThat(r.actualizados()).isEqualTo(1);
    }

    @Test
    void sinFilasNoHaceNada() {
        assertThat(service.importar(List.of(), Map.of(), 1)).isEqualTo(new ResultadoImportacion(0, 0, List.of()));
    }

    static class Duplicado extends ExcepcionNegocio {
        Duplicado() { super("codigo-barras-duplicado", HttpStatus.CONFLICT, "El código de barras ya existe"); }
    }
}
