package cl.primerplayer.catalogo.infrastructure.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import cl.primerplayer.catalogo.api.ProductoAdmin;

@Service
public class ProductosExcelService {

    public byte[] exportarStock(List<ProductoAdmin> productos) {
        try (Workbook workbook = crearWorkbook(productos);
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            workbook.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el Excel del stock.", e);
        }
    }

    Workbook crearWorkbook(List<ProductoAdmin> productos) {
        Workbook workbook = new XSSFWorkbook();
        var hoja = workbook.createSheet("Productos");
        CellStyle estilo = estiloEncabezado(workbook);
        String[] encabezados = {
                "Nombre", "Codigo", "Precio", "Stock", "Categoria", "Descripcion", "Publicado",
                "LimitePorUsuario", "Franquicia", "Marca", "Tipo", "Plataforma", "Idioma", "Linea"
        };
        Row cabecera = hoja.createRow(0);
        for (int i = 0; i < encabezados.length; i++) {
            Cell celda = cabecera.createCell(i);
            celda.setCellValue(encabezados[i]);
            celda.setCellStyle(estilo);
        }

        for (int i = 0; i < productos.size(); i++) {
            ProductoAdmin p = productos.get(i);
            Row fila = hoja.createRow(i + 1);
            texto(fila, 0, p.nombre());
            texto(fila, 1, p.codigoBarras());
            numero(fila, 2, p.precio());
            numero(fila, 3, p.stock());
            texto(fila, 4, p.categoria());
            texto(fila, 5, p.descripcion());
            texto(fila, 6, p.publicado() ? "Sí" : "No");
            numeroOpcional(fila, 7, p.limitePorCliente());
            texto(fila, 8, p.franquicia());
            texto(fila, 9, p.marca());
            texto(fila, 10, p.tipo());
            texto(fila, 11, p.plataforma());
            texto(fila, 12, p.idioma());
            texto(fila, 13, p.linea());
        }
        for (int i = 0; i < encabezados.length; i++) {
            hoja.autoSizeColumn(i);
        }
        return workbook;
    }

    private static CellStyle estiloEncabezado(Workbook workbook) {
        CellStyle estilo = workbook.createCellStyle();
        estilo.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estilo.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.THIN);
        estilo.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
        estilo.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
        estilo.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);
        var fuente = workbook.createFont();
        fuente.setBold(true);
        fuente.setColor(IndexedColors.WHITE.getIndex());
        estilo.setFont(fuente);
        return estilo;
    }

    private static void texto(Row fila, int columna, String valor) {
        if (valor != null) {
            fila.createCell(columna).setCellValue(valor);
        }
    }

    private static void numero(Row fila, int columna, long valor) {
        fila.createCell(columna).setCellValue(valor);
    }

    private static void numeroOpcional(Row fila, int columna, Integer valor) {
        if (valor != null) {
            fila.createCell(columna).setCellValue(valor);
        }
    }
}
