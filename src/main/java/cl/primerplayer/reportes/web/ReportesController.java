package cl.primerplayer.reportes.web;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.primerplayer.reportes.api.ReportesUseCase;
import cl.primerplayer.reportes.api.ReportesUseCase.ReporteDia;
import cl.primerplayer.reportes.api.ReportesUseCase.ReportePeriodo;
import cl.primerplayer.reportes.api.ReportesUseCase.Tramo;
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

    @GetMapping("/por-dia")
    @Operation(summary = "Ventas por día del mes", description = "mes en formato AAAA-MM (p. ej. 2026-11)")
    ReportePeriodo porDia(@RequestParam String mes) {
        try {
            return reportes.porDia(YearMonth.parse(mes));
        } catch (DateTimeParseException e) {
            throw new ValidacionException("mes", "Usa el formato AAAA-MM.");
        }
    }

    @GetMapping("/por-mes")
    @Operation(summary = "Ventas por mes del año")
    ReportePeriodo porMes(@RequestParam int anio) {
        return reportes.porMes(anio);
    }

    private static LocalDate fecha(String valor) {
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            throw new ValidacionException("fecha", "Usa el formato AAAA-MM-DD.");
        }
    }
}
