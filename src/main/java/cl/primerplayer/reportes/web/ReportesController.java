package cl.primerplayer.reportes.web;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.primerplayer.reportes.api.ReportesUseCase;
import cl.primerplayer.reportes.api.ReportesUseCase.ReporteDia;
import cl.primerplayer.reportes.api.ReportesUseCase.ReportePeriodo;
import cl.primerplayer.reportes.api.ReportesUseCase.Tramo;
import cl.primerplayer.reportes.infrastructure.excel.ReportesExcelService;
import cl.primerplayer.shared.api.ValidacionException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Reportes de ventas (solo ADMIN). Fechas = días de Chile. Solo ventas válidas (no anuladas, rechazadas ni vencidas). */
@RestController
@RequestMapping("/api/v1/admin/reportes")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin · Reportes")
@SecurityRequirement(name = "bearer")
class ReportesController {

    private final ReportesUseCase reportes;
    private final ReportesExcelService reportesExcel;

    @GetMapping("/dia")
    @Operation(summary = "Resumen del día: total, variación vs. día anterior, órdenes, ticket promedio, productos vs. eventos, "
            + "por método de pago y top 5 por unidades")
    ReporteDia dia(@RequestParam String fecha) {
        return reportes.dia(fecha(fecha));
    }

    @GetMapping("/por-hora")
    @Operation(summary = "Ventas por hora del día (24 tramos, 0 a 23)")
    List<Tramo> porHora(@RequestParam String fecha) {
        return reportes.porHora(fecha(fecha));
    }

    @GetMapping(value = "/por-dia", produces = {
            MediaType.APPLICATION_JSON_VALUE,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    })
    @Operation(summary = "Ventas por día del mes", description = "mes en formato AAAA-MM (p. ej. 2026-11)")
    ResponseEntity<?> porDia(@RequestParam(required = false) String mes,
                             @RequestParam(required = false) Integer year,
                             @RequestParam(required = false) Integer month,
                             @RequestParam(defaultValue = "json") String format,
                             @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        YearMonth periodo;
        if (year == null && month == null && (mes == null || mes.isBlank())) {
            throw new ValidacionException("mes", "Envía mes=AAAA-MM o los parámetros year y month.");
        }
        try {
            periodo = year != null || month != null
                    ? YearMonth.of(required(year, "year"), required(month, "month"))
                    : YearMonth.parse(mes);
        } catch (DateTimeParseException e) {
            throw new ValidacionException("mes", "Usa el formato AAAA-MM.");
        } catch (java.time.DateTimeException e) {
            throw new ValidacionException("month", "El año y mes deben formar un período válido.");
        }
        ReportePeriodo reporte = reportes.porDia(periodo);
        if (!solicitaExcel(format, accept)) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(reporte);
        }
        return excel(reportesExcel.exportarReporteMes(reporte, periodo.getYear(), periodo.getMonthValue()),
                reportesExcel.nombreArchivoMes(periodo.getYear(), periodo.getMonthValue()));
    }

    @GetMapping(value = "/por-mes", produces = {
            MediaType.APPLICATION_JSON_VALUE,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    })
    @Operation(summary = "Ventas por mes del año")
    ResponseEntity<?> porMes(@RequestParam(required = false) Integer anio,
                             @RequestParam(required = false) Integer year,
                             @RequestParam(defaultValue = "json") String format,
                             @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        int periodo = required(year != null ? year : anio, "year");
        ReportePeriodo reporte = reportes.porMes(periodo);
        if (!solicitaExcel(format, accept)) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(reporte);
        }
        return excel(reportesExcel.exportarReporteAnio(reporte, periodo), reportesExcel.nombreArchivoAnio(periodo));
    }

    private static int required(Integer valor, String campo) {
        if (valor == null) {
            throw new ValidacionException(campo, "El parámetro es obligatorio.");
        }
        return valor;
    }

    private static boolean solicitaExcel(String format, String accept) {
        if ("excel".equalsIgnoreCase(format)) {
            return true;
        }
        return accept != null && MediaType.parseMediaTypes(accept).stream()
                .anyMatch(tipo -> !tipo.isWildcardType() && !tipo.isWildcardSubtype()
                        && tipo.isCompatibleWith(MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
    }

    private static ResponseEntity<byte[]> excel(byte[] archivo, String nombre) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .body(archivo);
    }

    private static LocalDate fecha(String valor) {
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            throw new ValidacionException("fecha", "Usa el formato AAAA-MM-DD.");
        }
    }
}
