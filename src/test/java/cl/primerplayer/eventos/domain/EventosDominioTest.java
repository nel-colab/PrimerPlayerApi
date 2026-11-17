package cl.primerplayer.eventos.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import cl.primerplayer.eventos.api.DatosEvento;
import cl.primerplayer.eventos.api.SolicitudNoPendienteException;
import cl.primerplayer.eventos.api.SolicitudesCumpleanosUseCase.EstadoSolicitud;
import cl.primerplayer.eventos.api.TipoEvento;
import cl.primerplayer.shared.api.LimiteSoloAdminException;
import cl.primerplayer.shared.api.Rol;
import cl.primerplayer.shared.api.ValidacionException;

class EventosDominioTest {

    static final Instant AHORA = Instant.parse("2026-11-11T15:00:00Z");
    static final Instant INICIO = Instant.parse("2026-11-14T18:00:00Z");

    static DatosEvento datos(TipoEvento tipo, Instant fin, Integer cupos, Integer limite) {
        return new DatosEvento(" League Challenge ", tipo, INICIO, fin, "Tienda", 5000, cupos, limite,
                " Descripción ", List.of(" Trae tu mazo ", "  "), "", true);
    }

    // ---------- Bloque ----------

    @Test
    void bloque_esSemiabierto() {
        var a = new Bloque(INICIO, INICIO.plus(Duration.ofHours(2)));
        assertThat(a.seTraslapaCon(new Bloque(INICIO.plus(Duration.ofHours(1)), INICIO.plus(Duration.ofHours(3))))).isTrue();
        assertThat(a.seTraslapaCon(new Bloque(INICIO.plus(Duration.ofHours(2)), INICIO.plus(Duration.ofHours(3))))).isFalse();
        assertThatThrownBy(() -> new Bloque(INICIO, INICIO)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- Evento ----------

    @Test
    void crear_normalizaTextosYConsideraciones() {
        Evento e = Evento.crear(datos(TipoEvento.LEAGUE_CHALLENGE, null, 16, null), AHORA);
        assertThat(e.datos().titulo()).isEqualTo("League Challenge");
        assertThat(e.datos().consideraciones()).containsExactly("Trae tu mazo");
        assertThat(e.datos().imagenUrl()).isNull();
        assertThat(e.cuposDisponibles()).isEqualTo(16);
        assertThat(e.bloque(Duration.ofHours(3)).fin()).isEqualTo(INICIO.plus(Duration.ofHours(3)));
    }

    @Test
    void eventoConCupos_exigeAlMenosUnCupo() {
        assertThatThrownBy(() -> Evento.crear(datos(TipoEvento.LEAGUE_CUP, null, null, null), AHORA))
                .isInstanceOf(ValidacionException.class);
    }

    @Test
    void cumpleanos_exigeFinEIgnoraCuposPrecioYLimite() {
        assertThatThrownBy(() -> Evento.crear(datos(TipoEvento.CUMPLEANOS, null, null, null), AHORA))
                .isInstanceOf(ValidacionException.class);
        Evento e = Evento.crear(datos(TipoEvento.CUMPLEANOS, INICIO.plus(Duration.ofHours(3)), 20, 2), AHORA);
        assertThat(e.datos().cuposTotales()).isNull();
        assertThat(e.datos().precio()).isZero();
        assertThat(e.datos().limitePorCliente()).isNull();
        assertThat(e.cuposDisponibles()).isNull();
        assertThat(e.esCumpleanos()).isTrue();
    }

    @Test
    void finAnteriorAlInicio_validation() {
        assertThatThrownBy(() -> Evento.crear(datos(TipoEvento.OTROS, INICIO.minusSeconds(60), 5, null), AHORA))
                .isInstanceOf(ValidacionException.class);
    }

    @Test
    void actualizar_limiteYCuposVendidos() {
        Evento base = Evento.crear(datos(TipoEvento.LEAGUE_CUP, null, 20, 2), AHORA);
        Evento vendido = Evento.reconstituir(1L, base.datos(), 8, 0, AHORA, null);

        assertThatThrownBy(() -> vendido.actualizar(datos(TipoEvento.LEAGUE_CUP, null, 20, 4), Rol.VENDEDOR, AHORA))
                .isInstanceOf(LimiteSoloAdminException.class);
        assertThat(vendido.actualizar(datos(TipoEvento.LEAGUE_CUP, null, 20, 4), Rol.ADMIN, AHORA)
                .datos().limitePorCliente()).isEqualTo(4);
        assertThatThrownBy(() -> vendido.actualizar(datos(TipoEvento.LEAGUE_CUP, null, 7, 2), Rol.ADMIN, AHORA))
                .isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> vendido.actualizar(
                datos(TipoEvento.CUMPLEANOS, INICIO.plus(Duration.ofHours(2)), null, null), Rol.ADMIN, AHORA))
                .isInstanceOf(ValidacionException.class);
    }

    // ---------- Solicitud de cumpleaños ----------

    static final SolicitudCumpleanos.Reglas REGLAS =
            new SolicitudCumpleanos.Reglas(Duration.ofHours(48), Duration.ofHours(1), Duration.ofHours(6));

    static SolicitudCumpleanos solicitud(Instant inicio, Duration duracion) {
        return SolicitudCumpleanos.nueva(7L, inicio, inicio.plus(duracion), " Martina ", 15, "+56912345678", " ",
                REGLAS, AHORA);
    }

    @Test
    void solicitud_validaAnticipacionYDuracion() {
        assertThat(solicitud(INICIO, Duration.ofHours(3)).estado()).isEqualTo(EstadoSolicitud.PENDIENTE);
        assertThatThrownBy(() -> solicitud(AHORA.plus(Duration.ofHours(47)), Duration.ofHours(2)))
                .isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> solicitud(INICIO, Duration.ofMinutes(30))).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> solicitud(INICIO, Duration.ofHours(7))).isInstanceOf(ValidacionException.class);
        assertThat(solicitud(INICIO, Duration.ofHours(3)).comentario()).isNull();
    }

    @Test
    void solicitud_maquinaDeEstados() {
        SolicitudCumpleanos s = solicitud(INICIO, Duration.ofHours(3));
        SolicitudCumpleanos aprobada = s.aprobar(99L, 1L, AHORA);
        assertThat(aprobada.estado()).isEqualTo(EstadoSolicitud.APROBADA);
        assertThat(aprobada.eventoId()).isEqualTo(99L);

        assertThatThrownBy(() -> aprobada.rechazar("x", 1L, AHORA)).isInstanceOf(SolicitudNoPendienteException.class);
        assertThatThrownBy(() -> aprobada.cancelar(AHORA)).isInstanceOf(SolicitudNoPendienteException.class);
        assertThatThrownBy(() -> s.rechazar(" ", 1L, AHORA)).isInstanceOf(ValidacionException.class);
        assertThat(s.cancelar(AHORA).estado()).isEqualTo(EstadoSolicitud.CANCELADA);
    }
}
