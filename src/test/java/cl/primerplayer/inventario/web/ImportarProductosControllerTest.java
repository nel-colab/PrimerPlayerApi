package cl.primerplayer.inventario.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import cl.primerplayer.inventario.api.ImportarProductosUseCase;
import cl.primerplayer.inventario.api.ImportarProductosUseCase.ErrorFila;
import cl.primerplayer.inventario.api.ImportarProductosUseCase.FilaImportacion;
import cl.primerplayer.inventario.api.ImportarProductosUseCase.ResultadoImportacion;
import cl.primerplayer.inventario.infrastructure.excel.ImportarArchivoExcelService;
import cl.primerplayer.inventario.infrastructure.excel.ImportarArchivoExcelService.LecturaImportacion;
import cl.primerplayer.shared.api.Rol;
import cl.primerplayer.shared.api.UsuarioActual;
import cl.primerplayer.shared.api.ValidacionException;

/** Elección de formato y manejo de archivos ilegibles (la importación misma se prueba en ImportarProductosIntegracionTest). */
class ImportarProductosControllerTest {

    static final UsuarioActual ADMIN = new UsuarioActual(7, "admin@primerplayer.cl", Rol.ADMIN);
    static final ResultadoImportacion OK = new ResultadoImportacion(1, 0, List.of());
    static final FilaImportacion FILA = new FilaImportacion("Sobre", null, 4990L, 3, "tcg",
            null, true, null, null, null, null, null, null, null);
    static final byte[] CONTENIDO = "x".getBytes();

    final ImportarProductosUseCase importar = mock(ImportarProductosUseCase.class);
    final ImportarArchivoExcelService archivos = mock(ImportarArchivoExcelService.class);
    final ImportarProductosController controller = new ImportarProductosController(importar, archivos);

    final LecturaImportacion lectura = new LecturaImportacion(List.of(FILA), List.of(new ErrorFila(3, "precio inválido")));

    @BeforeEach
    void preparar() throws IOException {
        when(importar.importar(any(), anyMap(), anyLong())).thenReturn(OK);
        when(archivos.parsearCsv(any())).thenReturn(lectura);
        when(archivos.parsearExcel(any())).thenReturn(lectura);
    }

    static MockMultipartFile archivo(String nombre, String tipo) {
        return new MockMultipartFile("archivo", nombre, tipo, CONTENIDO);
    }

    // ---------- JSON ----------

    @Test
    void jsonConFilasLasImporta() {
        var r = controller.importarJson(new ImportarProductosController.ImportarRequest(List.of(FILA)), ADMIN);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r.getBody()).isEqualTo(OK);
        verify(importar).importar(List.of(FILA), Map.of(), 7L);
    }

    @Test
    void jsonSinCuerpoOSinFilasImportaListaVacia() {
        controller.importarJson(null, ADMIN);
        controller.importarJson(new ImportarProductosController.ImportarRequest(null), ADMIN);

        verify(importar, org.mockito.Mockito.times(2)).importar(List.of(), Map.of(), 7L);
    }

    // ---------- archivo: formato ----------

    @Test
    void csvPorTipoText() throws IOException {
        controller.importarArchivo(archivo("datos", "text/csv"), ADMIN);
        verify(archivos).parsearCsv(any());
        verify(importar).importar(List.of(FILA), Map.of(3, "precio inválido"), 7L);
    }

    @Test
    void csvPorTipoApplication() throws IOException {
        controller.importarArchivo(archivo("datos", "APPLICATION/CSV"), ADMIN);
        verify(archivos).parsearCsv(any());
    }

    @Test
    void csvPorExtensionAunqueElTipoSeaOtro() throws IOException {
        controller.importarArchivo(archivo("Productos.CSV", "text/plain"), ADMIN);
        verify(archivos).parsearCsv(any());
    }

    @Test
    void xlsxPorTipoOficial() throws IOException {
        var r = controller.importarArchivo(
                archivo("datos", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), ADMIN);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(archivos).parsearExcel(any());
        verify(archivos, never()).parsearCsv(any());
    }

    @Test
    void xlsxPorOctetStream() throws IOException {
        controller.importarArchivo(archivo("datos", "application/octet-stream"), ADMIN);
        verify(archivos).parsearExcel(any());
    }

    @Test
    void xlsxPorExtensionSinTipo() throws IOException {
        controller.importarArchivo(archivo("stock.xlsx", null), ADMIN);
        verify(archivos).parsearExcel(any());
    }

    @Test
    void sinTipoNiNombreReconocibleEsFormatoInvalido() {
        assertThatThrownBy(() -> controller.importarArchivo(archivo(null, null), ADMIN))
                .isInstanceOf(ValidacionException.class)
                .hasMessageContaining("XLSX o CSV");
        assertThatThrownBy(() -> controller.importarArchivo(archivo("foto.png", "image/png"), ADMIN))
                .isInstanceOf(ValidacionException.class);
        verifyNoInteractions(importar);
    }

    // ---------- archivo: errores ----------

    @Test
    void archivoVacioSeRechazaSinLeerlo() {
        var vacio = new MockMultipartFile("archivo", "a.csv", "text/csv", new byte[0]);

        assertThatThrownBy(() -> controller.importarArchivo(vacio, ADMIN))
                .isInstanceOf(ValidacionException.class)
                .hasMessageContaining("vacío");
        verifyNoInteractions(archivos, importar);
    }

    @Test
    void archivoIlegibleEsErrorDeValidacion() throws IOException {
        when(archivos.parsearExcel(any())).thenThrow(new IOException("zip corrupto"));

        assertThatThrownBy(() -> controller.importarArchivo(archivo("a.xlsx", null), ADMIN))
                .isInstanceOf(ValidacionException.class)
                .hasMessageContaining("No se pudo leer");
    }

    @Test
    void contenidoInvalidoEsErrorDeValidacion() throws IOException {
        when(archivos.parsearCsv(any())).thenThrow(new IllegalArgumentException("columnas"));

        assertThatThrownBy(() -> controller.importarArchivo(archivo("a.csv", null), ADMIN))
                .isInstanceOf(ValidacionException.class);
    }

    @Test
    void errorAlAbrirElArchivoEsErrorDeValidacion() throws IOException {
        MultipartFile roto = mock(MultipartFile.class);
        when(roto.isEmpty()).thenReturn(false);
        when(roto.getOriginalFilename()).thenReturn("a.csv");
        when(roto.getInputStream()).thenThrow(new IOException("disco"));

        assertThatThrownBy(() -> controller.importarArchivo(roto, ADMIN)).isInstanceOf(ValidacionException.class);
        verify(archivos, never()).parsearCsv(any(InputStream.class));
        verify(importar, never()).importar(any(), anyMap(), eq(7L));
    }
}
