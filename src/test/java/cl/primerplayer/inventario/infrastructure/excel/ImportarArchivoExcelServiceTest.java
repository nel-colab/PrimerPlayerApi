package cl.primerplayer.inventario.infrastructure.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ImportarArchivoExcelServiceTest {

    private final ImportarArchivoExcelService importar = new ImportarArchivoExcelService();

    @Test
    void parsearExcel_leeDosFilasYConviertePublicado() throws Exception {
        byte[] archivo;
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Productos");
            String[] encabezados = { "Nombre", "Codigo", "Precio", "Stock", "Categoria", "Publicado",
                    "LimitePorUsuario", "Idioma", "Linea" };
            var cabecera = sheet.createRow(0);
            for (int i = 0; i < encabezados.length; i++) {
                cabecera.createCell(i).setCellValue(encabezados[i]);
            }
            var primera = sheet.createRow(1);
            primera.createCell(0).setCellValue("Producto A");
            primera.createCell(2).setCellValue(4990);
            primera.createCell(3).setCellValue(8);
            primera.createCell(4).setCellValue("tcg");
            primera.createCell(5).setCellValue("Sí");
            primera.createCell(7).setCellValue("Español");
            primera.createCell(8).setCellValue("Línea A");
            var segunda = sheet.createRow(2);
            segunda.createCell(0).setCellValue("Producto B");
            segunda.createCell(2).setCellValue(1200);
            segunda.createCell(3).setCellValue(2);
            workbook.write(salida);
            archivo = salida.toByteArray();
        }

        var lectura = importar.parsearExcel(new ByteArrayInputStream(archivo));

        assertThat(lectura.filas()).hasSize(2);
        assertThat(lectura.errores()).isEmpty();
        assertThat(lectura.filas().get(0).nombre()).isEqualTo("Producto A");
        assertThat(lectura.filas().get(0).precio()).isEqualTo(4990);
        assertThat(lectura.filas().get(0).stock()).isEqualTo(8);
        assertThat(lectura.filas().get(0).categoria()).isEqualTo("tcg");
        assertThat(lectura.filas().get(0).publicado()).isTrue();
        assertThat(lectura.filas().get(1).publicado()).isFalse();
    }

    @Test
    void parsearCsv_admiteSeparadorPuntoYComaYRegistraNumeroInvalidoPorFila() throws Exception {
        String csv = "Nombre;Precio;Stock;Categoria;Publicado\nProducto A;4.990;3;tcg;si\nProducto B;inválido;1;tcg;no";

        var lectura = importar.parsearCsv(new ByteArrayInputStream(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertThat(lectura.filas()).hasSize(2);
        assertThat(lectura.filas().get(0).precio()).isEqualTo(4990);
        assertThat(lectura.filas().get(0).publicado()).isTrue();
        assertThat(lectura.errores()).containsExactly(
                new cl.primerplayer.inventario.api.ImportarProductosUseCase.ErrorFila(2, "Precio inválido"));
    }
}
