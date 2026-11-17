package cl.primerplayer.caja.web;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.primerplayer.caja.api.CajaUseCase;
import cl.primerplayer.caja.api.CajaUseCase.Cierre;
import cl.primerplayer.shared.api.FechasTienda;
import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.UsuarioActual;
import cl.primerplayer.shared.api.ValidacionException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/** Cierre de caja (VENDEDOR o ADMIN). Fechas = días de Chile en formato {@code YYYY-MM-DD}. */
@RestController
@RequestMapping("/api/v1/admin/caja")
@PreAuthorize("hasAnyRole('VENDEDOR','ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Caja")
@SecurityRequirement(name = "bearer")
class CajaController {

    private final CajaUseCase caja;

    @GetMapping("/cierres")
    @Operation(summary = "Historial de cierres (más reciente primero)")
    Pagina<CierreResponse> cierres(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "30") int size) {
        return caja.cierres(page, size).map(CierreResponse::de);
    }

    @GetMapping("/{fecha}")
    @Operation(summary = "Resumen del día: ventas, unidades, total, subtotales por método, efectivo neto y cierre si existe",
            description = "Las ventas web cuentan en el total y en subtotales (método webpay), no en el efectivo de la caja.")
    ResumenResponse resumen(@PathVariable String fecha) {
        var r = caja.resumen(fecha(fecha));
        return new ResumenResponse(r.fecha(), r.numeroVentas(), r.unidades(), r.total(), r.totalTienda(), r.totalWeb(),
                r.subtotales(), r.pagosEfectivo(), r.efectivoNeto(),
                r.productos().stream().map(p -> new ResumenResponse.Producto(p.tipo(), p.itemId(), p.nombre(),
                        p.unidades(), p.monto())).toList(),
                r.cierre() == null ? null : CierreResponse.de(r.cierre()));
    }

    @PutMapping("/{fecha}/cierre")
    @Operation(summary = "Cerrar (o volver a cerrar) la caja del día",
            description = "El servidor calcula esperado = fondo inicial + efectivo neto y diferencia = contado − esperado. "
                    + "Volver a cerrar guarda la versión anterior en el historial.")
    CierreResponse cerrar(@PathVariable String fecha, @Valid @RequestBody CierreRequest req,
                          @AuthenticationPrincipal UsuarioActual usuario) {
        return CierreResponse.de(caja.cerrar(fecha(fecha), req.fondoInicial(), req.efectivoContado(),
                req.observaciones(), usuario.id()));
    }

    private static LocalDate fecha(String valor) {
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            throw new ValidacionException("fecha", "Usa el formato AAAA-MM-DD.");
        }
    }

    // ---------- DTO ----------

    record CierreRequest(
            @Schema(example = "50000") @NotNull Long fondoInicial,
            @Schema(example = "171990") @NotNull Long efectivoContado,
            @Schema(example = "Retiro de $20.000 para cambio") String observaciones) {}

    record ResumenResponse(LocalDate fecha, int sales, int units, long total, long storeTotal, long webTotal,
                           @Schema(description = "Monto por método (efectivo, debito, …, webpay)") Map<String, Long> byMethod,
                           int cashPayments, @Schema(description = "Σ(recibido − vuelto) de ventas de mostrador") long cashNet,
                           @Schema(description = "Productos y eventos vendidos, por unidades") List<Producto> items,
                           @Schema(description = "null si el día aún no se cierra") CierreResponse closure) {

        record Producto(String type, long itemId, String name, int units, long amount) {}
    }

    /** Claves alineadas con {@code CashClosure} del frontend. */
    record CierreResponse(LocalDate date,
                          @Schema(description = "Fondo inicial") long openingCash,
                          @Schema(description = "Efectivo contado") long countedCash,
                          @Schema(description = "Σ(recibido − vuelto) del día") long cashNet,
                          @Schema(description = "openingCash + cashNet") long expectedCash,
                          @Schema(description = "countedCash − expectedCash") long difference,
                          @Schema(description = "cuadrada | sobrante | faltante") String status,
                          long totalSales, int orderCount, Map<String, Long> byMethod, String notes,
                          @Schema(description = "Sube cada vez que se vuelve a cerrar el mismo día") int version,
                          String closedBy, OffsetDateTime closedAt) {

        static CierreResponse de(Cierre c) {
            return new CierreResponse(c.fecha(), c.fondoInicial(), c.efectivoContado(), c.efectivoNeto(),
                    c.esperadoEnCaja(), c.diferencia(), c.estado(), c.totalVentas(), c.numeroVentas(), c.subtotales(),
                    c.observaciones(), c.version(), c.cerradoPor(), FechasTienda.enZona(c.cerradoEn()));
        }
    }
}
