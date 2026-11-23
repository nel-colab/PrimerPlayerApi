package cl.primerplayer.reportes.infrastructure.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import cl.primerplayer.reportes.api.ReportesUseCase.ReportePeriodo;
import cl.primerplayer.reportes.api.ReportesUseCase.Tramo;

@Service
public class ReportesExcelService {

    public byte[] exportarReporteMes(ReportePeriodo reporte, int anio, int mes) {
        return exportar(reporte, "Resumen del mes", "Por día",
                new String[] { "Mes", "Total ventas", "Nº pedidos", "Promedio" },
                new Object[] { reporte.periodo(), reporte.total(), reporte.ordenes(), reporte.ticketPromedio() },
                new String[] { "Día", "Total", "Pedidos", "Promedio" },
                reporte.tramos(), false);
    }

    public byte[] exportarReporteAnio(ReportePeriodo reporte, int anio) {
        return exportar(reporte, "Resumen del año", "Por mes",
                new String[] { "Año", "Total ventas", "Nº pedidos", "Promedio" },
                new Object[] { anio, reporte.total(), reporte.ordenes(), reporte.ticketPromedio() },
                new String[] { "Mes", "Nombre", "Total", "Pedidos", "Promedio" },
                reporte.tramos(), true);
    }

    private byte[] exportar(ReportePeriodo reporte, String hojaResumen, String hojaDetalle,
                            String[] encabezadosResumen, Object[] resumen, String[] encabezadosDetalle,
                            List<Tramo> tramos, boolean meses) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            CellStyle encabezado = estiloEncabezado(workbook);
            CellStyle moneda = estiloMoneda(workbook);
            var resumenSheet = workbook.createSheet(hojaResumen);
            escribirCabecera(resumenSheet.createRow(0), encabezadosResumen, encabezado);
            Row filaResumen = resumenSheet.createRow(1);
            for (int i = 0; i < resumen.length; i++) {
                escribir(filaResumen, i, resumen[i], i > 0, moneda);
            }
            autoSize(resumenSheet, encabezadosResumen.length);

            var detalleSheet = workbook.createSheet(hojaDetalle);
            escribirCabecera(detalleSheet.createRow(0), encabezadosDetalle, encabezado);
            for (int i = 0; i < tramos.size(); i++) {
                Tramo tramo = tramos.get(i);
                Row fila = detalleSheet.createRow(i + 1);
                int columna = 0;
                numero(fila, columna++, tramo.tramo(), null);
                if (meses) {
                    texto(fila, columna++, Month.of(tramo.tramo()).getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-CL")));
                }
                numero(fila, columna++, tramo.monto(), moneda);
                numero(fila, columna++, tramo.ordenes(), null);
                numero(fila, columna, tramo.ordenes() == 0 ? 0 : Math.round((double) tramo.monto() / tramo.ordenes()), moneda);
            }
            autoSize(detalleSheet, encabezadosDetalle.length);
            workbook.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el Excel del reporte.", e);
        }
    }

    public String nombreArchivoMes(int anio, int mes) {
        return "ventas-%04d-%02d.xlsx".formatted(anio, mes);
    }

    public String nombreArchivoAnio(int anio) {
        return "ventas-%04d.xlsx".formatted(anio);
    }

    private static void escribirCabecera(Row fila, String[] valores, CellStyle estilo) {
        for (int i = 0; i < valores.length; i++) {
            var celda = fila.createCell(i);
            celda.setCellValue(valores[i]);
            celda.setCellStyle(estilo);
        }
    }

    private static void escribir(Row fila, int columna, Object valor, boolean numerico, CellStyle moneda) {
        if (valor instanceof Number n) {
            numero(fila, columna, n.doubleValue(), numerico ? moneda : null);
        } else if (valor != null) {
            texto(fila, columna, valor.toString());
        }
    }

    private static void numero(Row fila, int columna, double valor, CellStyle estilo) {
        var celda = fila.createCell(columna);
        celda.setCellValue(valor);
        if (estilo != null) {
            celda.setCellStyle(estilo);
        }
    }

    private static void texto(Row fila, int columna, String valor) {
        fila.createCell(columna).setCellValue(valor);
    }

    private static CellStyle estiloEncabezado(Workbook workbook) {
        CellStyle estilo = workbook.createCellStyle();
        estilo.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estilo.setBorderTop(BorderStyle.THIN);
        estilo.setBorderBottom(BorderStyle.THIN);
        estilo.setBorderLeft(BorderStyle.THIN);
        estilo.setBorderRight(BorderStyle.THIN);
        var fuente = workbook.createFont();
        fuente.setBold(true);
        fuente.setColor(IndexedColors.WHITE.getIndex());
        estilo.setFont(fuente);
        return estilo;
    }

    private static CellStyle estiloMoneda(Workbook workbook) {
        CellStyle estilo = workbook.createCellStyle();
        estilo.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
        return estilo;
    }

    private static void autoSize(org.apache.poi.ss.usermodel.Sheet sheet, int columnas) {
        for (int i = 0; i < columnas; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
