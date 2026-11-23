package cl.primerplayer.reportes.infrastructure.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.Month;
import java.util.List;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import cl.primerplayer.reportes.api.ReportesUseCase.ReportePeriodo;
import cl.primerplayer.reportes.api.ReportesUseCase.Tramo;

class ReportesExcelServiceTest {

    private final ReportesExcelService excel = new ReportesExcelService();

    @Test
    void exportarReporteMes_incluyeResumenDetalleYFormatoClp() throws Exception {
        var reporte = new ReportePeriodo("2026-11", 18_000, 3, 6_000,
                List.of(new Tramo(1, 2, 10_000), new Tramo(2, 1, 8_000)), List.of(), List.of());

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(
                excel.exportarReporteMes(reporte, 2026, 11)))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("Resumen del mes");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getNumericCellValue()).isEqualTo(18_000);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getCellStyle().getDataFormatString())
                    .isEqualTo("#,##0");
            assertThat(workbook.getSheetAt(1).getSheetName()).isEqualTo("Por día");
            assertThat(workbook.getSheetAt(1).getPhysicalNumberOfRows()).isEqualTo(3);
        }
    }

    @Test
    void exportarReporteAnio_escribeNombreDeMesEnEspanol() throws Exception {
        var reporte = new ReportePeriodo("2026", 20_000, 2, 10_000,
                List.of(new Tramo(1, 2, 20_000)), List.of(), List.of());

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(excel.exportarReporteAnio(reporte, 2026)))) {
            assertThat(workbook.getSheetAt(1).getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo(Month.JANUARY.getDisplayName(java.time.format.TextStyle.FULL,
                            java.util.Locale.forLanguageTag("es-CL")));
        }
    }
}
