package cl.primerplayer.inventario.infrastructure.excel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import cl.primerplayer.inventario.api.ImportarProductosUseCase.ErrorFila;
import cl.primerplayer.inventario.api.ImportarProductosUseCase.FilaImportacion;

@Service
public class ImportarArchivoExcelService {

    public LecturaImportacion parsearExcel(InputStream entrada) throws IOException {
        try (var workbook = WorkbookFactory.create(entrada)) {
            if (workbook.getNumberOfSheets() == 0) {
                return new LecturaImportacion(List.of(), List.of());
            }
            var sheet = workbook.getSheetAt(0);
            Row encabezado = sheet.getRow(sheet.getFirstRowNum());
            if (encabezado == null) {
                return new LecturaImportacion(List.of(), List.of());
            }
            DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("es-CL"));
            Map<String, Integer> columnas = columnasExcel(encabezado, formatter);
            validarEncabezado(columnas);
            List<FilaImportacion> filas = new ArrayList<>();
            List<ErrorFila> errores = new ArrayList<>();
            for (int numero = encabezado.getRowNum() + 1; numero <= sheet.getLastRowNum(); numero++) {
                Row fila = sheet.getRow(numero);
                if (fila == null || vacia(fila, formatter)) {
                    continue;
                }
                filas.add(convertir(numeroDato(filas.size()), clave -> valorExcel(fila, columnas, clave, formatter),
                        errores));
            }
            return new LecturaImportacion(filas, errores);
        }
    }

    public LecturaImportacion parsearCsv(InputStream entrada) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8));
        reader.mark(8192);
        String primera = reader.readLine();
        reader.reset();
        char delimitador = primera != null && primera.contains(";") && !primera.contains(",") ? ';' : ',';
        CSVFormat formato = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .setDelimiter(delimitador)
                .get();
        try (CSVParser parser = formato.parse(reader)) {
            Map<String, Integer> columnas = columnasCsv(parser.getHeaderNames());
            validarEncabezado(columnas);
            List<FilaImportacion> filas = new ArrayList<>();
            List<ErrorFila> errores = new ArrayList<>();
            for (var registro : parser) {
                filas.add(convertir(numeroDato(filas.size()), clave -> valorCsv(registro, columnas, clave), errores));
            }
            return new LecturaImportacion(filas, errores);
        }
    }

    private static FilaImportacion convertir(int numeroFila, LectorCampo leer, List<ErrorFila> errores) {
        String nombre = leer.valor("nombre");
        String codigo = leer.valor("codigo");
        Numero precio = entero(leer.valor("precio"), numeroFila, "Precio inválido", errores);
        Numero stock = entero(leer.valor("stock"), numeroFila, "Stock inválido", errores);
        Numero limite = entero(leer.valor("limite"), numeroFila, "Límite por usuario inválido", errores);
        return new FilaImportacion(nombre, codigo, precio.largo(), stock.entero(), leer.valor("categoria"),
                leer.valor("descripcion"), publicado(leer.valor("publicado")), limite.entero(),
                leer.valor("franquicia"), leer.valor("marca"), leer.valor("tipo"), leer.valor("plataforma"),
                leer.valor("idioma"), leer.valor("linea"));
    }

    private static Numero entero(String texto, int fila, String mensaje, List<ErrorFila> errores) {
        if (texto == null || texto.isBlank()) {
            return new Numero(null, null);
        }
        try {
            String valor = texto.strip().replace("$", "").replace(" ", "");
            if (valor.matches("[+-]?\\d{1,3}([.,]\\d{3})+")) {
                valor = valor.replace(".", "").replace(",", "");
            }
            long numero = new BigDecimal(valor).longValueExact();
            if (numero < Integer.MIN_VALUE || numero > Integer.MAX_VALUE) {
                if (mensaje.startsWith("Precio")) {
                    return new Numero(numero, null);
                }
                throw new ArithmeticException("fuera de rango");
            }
            return new Numero(numero, (int) numero);
        } catch (NumberFormatException | ArithmeticException e) {
            errores.add(new ErrorFila(fila, mensaje));
            return new Numero(null, null);
        }
    }

    private static Boolean publicado(String texto) {
        String valor = normalizar(texto == null ? "" : texto);
        return valor.equals("si") || valor.equals("s") || valor.equals("true");
    }

    private static Map<String, Integer> columnasExcel(Row encabezado, DataFormatter formatter) {
        Map<String, Integer> columnas = new HashMap<>();
        for (int i = 0; i < encabezado.getLastCellNum(); i++) {
            columnas.put(normalizar(formatter.formatCellValue(encabezado.getCell(i))), i);
        }
        return columnas;
    }

    private static Map<String, Integer> columnasCsv(List<String> encabezados) {
        Map<String, Integer> columnas = new HashMap<>();
        for (int i = 0; i < encabezados.size(); i++) {
            columnas.put(normalizar(encabezados.get(i)), i);
        }
        return columnas;
    }

    private static void validarEncabezado(Map<String, Integer> columnas) throws IOException {
        if (buscarColumna(columnas, "nombre") == null) {
            throw new IOException("Falta la columna Nombre.");
        }
    }

    private static String valorExcel(Row fila, Map<String, Integer> columnas, String clave, DataFormatter formatter) {
        Integer indice = buscarColumna(columnas, clave);
        if (indice == null || fila.getCell(indice) == null) {
            return null;
        }
        String valor = formatter.formatCellValue(fila.getCell(indice)).strip();
        return valor.isEmpty() ? null : valor;
    }

    private static String valorCsv(org.apache.commons.csv.CSVRecord fila, Map<String, Integer> columnas, String clave) {
        Integer indice = buscarColumna(columnas, clave);
        if (indice == null || indice >= fila.size()) {
            return null;
        }
        String valor = fila.get(indice).strip();
        return valor.isEmpty() ? null : valor;
    }

    private static Integer buscarColumna(Map<String, Integer> columnas, String clave) {
        return switch (clave) {
            case "nombre" -> primero(columnas, "nombre");
            case "codigo" -> primero(columnas, "codigo", "codigobarras", "codigodebarras");
            case "precio" -> primero(columnas, "precio");
            case "stock" -> primero(columnas, "stock");
            case "categoria" -> primero(columnas, "categoria");
            case "descripcion" -> primero(columnas, "descripcion");
            case "publicado" -> primero(columnas, "publicado");
            case "limite" -> primero(columnas, "limiteporusuario", "limiteporcliente");
            case "franquicia" -> primero(columnas, "franquicia");
            case "marca" -> primero(columnas, "marca");
            case "tipo" -> primero(columnas, "tipo");
            case "plataforma" -> primero(columnas, "plataforma");
            case "idioma" -> primero(columnas, "idioma");
            case "linea" -> primero(columnas, "linea");
            default -> null;
        };
    }

    private static Integer primero(Map<String, Integer> columnas, String... nombres) {
        for (String nombre : nombres) {
            Integer indice = columnas.get(nombre);
            if (indice != null) {
                return indice;
            }
        }
        return null;
    }

    private static boolean vacia(Row fila, DataFormatter formatter) {
        for (int i = 0; i < fila.getLastCellNum(); i++) {
            if (!formatter.formatCellValue(fila.getCell(i)).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static int numeroDato(int indice) {
        return indice + 1;
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto.strip().replace("\uFEFF", ""), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    @FunctionalInterface
    private interface LectorCampo {
        String valor(String clave);
    }

    private record Numero(Long largo, Integer entero) {}

    public record LecturaImportacion(List<FilaImportacion> filas, List<ErrorFila> errores) {
        public LecturaImportacion {
            filas = List.copyOf(filas);
            errores = List.copyOf(errores);
        }

        public Map<Integer, String> erroresPorFila() {
            Map<Integer, String> resultado = new HashMap<>();
            errores.forEach(error -> resultado.merge(error.fila(), error.mensaje(),
                    (actual, siguiente) -> actual + "; " + siguiente));
            return Map.copyOf(resultado);
        }
    }
}
