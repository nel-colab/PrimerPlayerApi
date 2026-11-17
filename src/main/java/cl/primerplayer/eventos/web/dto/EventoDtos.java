package cl.primerplayer.eventos.web.dto;

import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** DTO del módulo eventos (formulario "Nuevo evento", tarjetas públicas y solicitudes de cumpleaños). */
public final class EventoDtos {

    private EventoDtos() {}

    /** Formulario "Nuevo evento". Para {@code cumpleanos}: {@code fechaFin} obligatoria; cupos y precio se ignoran. */
    public record EventoRequest(
            @Schema(example = "League Challenge de Noviembre", description = "Nombre del evento")
            @NotBlank @Size(max = 160) String titulo,
            @Schema(example = "league-challenge", allowableValues = {"league-challenge", "league-cup", "torneo-tcg",
                    "prelanzamiento", "lanzamiento", "videojuegos", "cumpleanos", "otros"})
            @NotBlank String tipo,
            @Schema(example = "2026-11-14T15:00", description = "ISO 8601; sin zona = hora de Chile")
            @NotBlank String fecha,
            @Schema(example = "2026-11-14T19:00", description = "Hora de término (obligatoria para cumpleaños)")
            String fechaFin,
            @Schema(example = "Av. Portales Ote. 1126, San Bernardo") @NotBlank @Size(max = 160) String lugar,
            @Schema(example = "16", description = "Cupos disponibles (máximo de participantes)")
            @Min(1) @Max(10_000) Integer cuposTotales,
            @Schema(example = "5000", description = "Precio por cupo en CLP; 0 = gratuito")
            @Min(0) @Max(100_000_000) Long precio,
            @Schema(example = "2", description = "Vacío = sin límite; se acumula entre compras")
            @Min(1) @Max(999) Integer limitePorUsuario,
            @Schema(example = "Pon a prueba tu mazo en una tarde de estrategia y Pokémon TCG junto a la comunidad.")
            @NotBlank @Size(max = 2_000) String descripcion,
            @Schema(example = "[\"Trae tu mazo preparado para jugar.\"]")
            @Size(max = 10) List<@Size(max = 200) String> consideraciones,
            @Schema(example = "https://cdn.primerplayer.cl/eventos/league-challenge.webp")
            @Size(max = 500) String imagen,
            Boolean publicado) {
    }

    /** Tarjeta del evento. Las fechas van con el desfase de Chile. */
    public record EventoResponse(
            long id,
            String titulo,
            String tipo,
            OffsetDateTime fecha,
            OffsetDateTime fechaFin,
            String lugar,
            long precio,
            Integer cuposTotales,
            Integer cuposDisponibles,
            Integer limitePorUsuario,
            String descripcion,
            List<String> consideraciones,
            String imagen,
            @Schema(description = "true en cumpleaños: la tienda está cerrada al público en el bloque") boolean tiendaCerrada,
            boolean publicado,
            @Schema(description = "Solo admin") Integer cuposOcupados,
            @Schema(description = "Solo admin; también en el header ETag") Integer version) {
    }

    public record PublicacionRequest(@NotNull Boolean publicado) {}

    public record BloqueOcupadoResponse(OffsetDateTime inicio, OffsetDateTime fin, String tipo, boolean tiendaCerrada) {}

    // ---------- solicitudes de cumpleaños ----------

    public record SolicitudRequest(
            @Schema(example = "2026-12-12T15:00") @NotBlank String inicio,
            @Schema(example = "2026-12-12T18:00") @NotBlank String fin,
            @Schema(example = "Martina (8 años)") @NotBlank @Size(max = 80) String festejado,
            @Schema(example = "15") @NotNull @Min(1) @Max(200) Integer invitados,
            @Schema(example = "+56912345678") @NotBlank @Size(max = 20) String telefono,
            @Schema(example = "Queremos torneo de Pokémon para los niños") @Size(max = 500) String comentario) {
    }

    public record SolicitudResponse(
            long id,
            OffsetDateTime inicio,
            OffsetDateTime fin,
            String festejado,
            int invitados,
            String telefono,
            String comentario,
            @Schema(allowableValues = {"PENDIENTE", "APROBADA", "RECHAZADA", "CANCELADA"}) String estado,
            String motivoRechazo,
            Long eventoId,
            OffsetDateTime resueltaEn,
            OffsetDateTime creadoEn,
            @Schema(description = "Solo en la bandeja de la tienda") Long clienteId) {
    }

    /** Todo opcional: por defecto "Cumpleaños privado · tienda cerrada" y la descripción del cierre. */
    public record AprobacionRequest(
            @Size(max = 160) String titulo,
            @Size(max = 2_000) String descripcion,
            @Size(max = 10) List<@Size(max = 200) String> consideraciones,
            Boolean publicado) {
    }

    public record RechazoRequest(@NotBlank @Size(max = 300) String motivo) {}
}
