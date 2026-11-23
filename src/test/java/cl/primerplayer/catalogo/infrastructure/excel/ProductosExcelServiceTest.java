package cl.primerplayer.catalogo.infrastructure.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import cl.primerplayer.catalogo.api.ProductoAdmin;

class ProductosExcelServiceTest {

    private final ProductosExcelService excel = new ProductosExcelService();

    @Test
    void exportarStock_generaHojaColumnasYProductos() throws Exception {
        var productos = List.of(
                producto("Producto A", 1000, 3, true),
                producto("Producto B", 2000, 0, false),
                producto("Producto C", 3000, 5, true));

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(excel.exportarStock(productos)))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            var sheet = workbook.getSheet("Productos");
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(4);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Nombre");
            assertThat(sheet.getRow(0).getCell(13).getStringCellValue()).isEqualTo("Linea");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Producto A");
            assertThat(sheet.getRow(1).getCell(6).getStringCellValue()).isEqualTo("Sí");
            assertThat(sheet.getRow(2).getCell(6).getStringCellValue()).isEqualTo("No");
            assertThat(sheet.getColumnWidth(0)).isGreaterThan(0);
        }
    }

    private static ProductoAdmin producto(String nombre, long precio, int stock, boolean publicado) {
        return new ProductoAdmin(1, nombre, null, precio, "tcg-pokemon", null, null, null, null, null, null,
                null, null, stock, 0, LocalDate.of(2026, 1, 1), publicado, List.of(), 0, Instant.EPOCH);
    }
}
