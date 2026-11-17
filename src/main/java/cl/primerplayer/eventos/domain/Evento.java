package cl.primerplayer.eventos.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import cl.primerplayer.eventos.api.DatosEvento;
import cl.primerplayer.eventos.api.TipoEvento;
import cl.primerplayer.shared.api.LimiteSoloAdminException;
import cl.primerplayer.shared.api.Rol;
import cl.primerplayer.shared.api.ValidacionException;

/**
 * Agregado Evento. Modelo puro e inmutable. {@code cuposOcupados} es de solo lectura: lo mueven las operaciones
 * atómicas de cupos, nunca el mantenedor.
 */
public final class Evento {

    public static final int MAX_CONSIDERACIONES = 10;
    public static final int MAX_LARGO_CONSIDERACION = 200;

    private final Long id;
    private final DatosEvento datos;
    private final int cuposOcupados;
    private final int version;
    private final Instant actualizadoEn;
    private final Instant eliminadoEn;

    private Evento(Long id, DatosEvento datos, int cuposOcupados, int version, Instant actualizadoEn,
                   Instant eliminadoEn) {
        this.id = id;
        this.datos = Objects.requireNonNull(datos);
        this.cuposOcupados = cuposOcupados;
        this.version = version;
        this.actualizadoEn = actualizadoEn;
        this.eliminadoEn = eliminadoEn;
    }

    public static Evento crear(DatosEvento datos, Instant ahora) {
        DatosEvento d = normalizar(datos);
        validar(d);
        return new Evento(null, d, 0, 0, ahora, null);
    }

    public static Evento reconstituir(Long id, DatosEvento datos, int cuposOcupados, int version,
                                      Instant actualizadoEn, Instant eliminadoEn) {
        return new Evento(id, datos, cuposOcupados, version, actualizadoEn, eliminadoEn);
    }

    /**
     * Reemplaza los datos. Reglas: el límite por cliente ya configurado solo lo cambia ADMIN; los cupos totales no
     * pueden bajar de los ya vendidos; un evento con cupos vendidos no puede pasar a cumpleaños.
     */
    public Evento actualizar(DatosEvento nuevos, Rol rol, Instant ahora) {
        DatosEvento d = normalizar(nuevos);
        validar(d);
        Integer limiteActual = datos.limitePorCliente();
        if (limiteActual != null && !limiteActual.equals(d.limitePorCliente()) && rol != Rol.ADMIN) {
            throw new LimiteSoloAdminException();
        }
        if (cuposOcupados > 0 && !d.tipo().vendeCupos()) {
            throw new ValidacionException("tipo", "El evento ya tiene cupos vendidos: no puede pasar a cumpleaños.");
        }
        if (d.cuposTotales() != null && d.cuposTotales() < cuposOcupados) {
            throw new ValidacionException("cuposTotales",
                    "No puede ser menor que los cupos ya vendidos (" + cuposOcupados + ").");
        }
        return new Evento(id, d, cuposOcupados, version, ahora, eliminadoEn);
    }

    public Evento conPublicacion(boolean publicado, Instant ahora) {
        DatosEvento d = datos;
        return new Evento(id, new DatosEvento(d.titulo(), d.tipo(), d.inicio(), d.fin(), d.lugar(), d.precio(),
                d.cuposTotales(), d.limitePorCliente(), d.descripcion(), d.consideraciones(), d.imagenUrl(), publicado),
                cuposOcupados, version, ahora, eliminadoEn);
    }

    public Evento eliminar(Instant ahora) {
        return new Evento(id, datos, cuposOcupados, version, ahora, ahora);
    }

    /**
     * Bloque que ocupa en la agenda. Si no tiene hora de término se usa la duración estimada.
     * TODO(pregunta-abierta): duración real de torneos y lanzamientos (hoy configurable, 3 h por defecto).
     */
    public Bloque bloque(Duration duracionEstimada) {
        return new Bloque(datos.inicio(), datos.fin() != null ? datos.fin() : datos.inicio().plus(duracionEstimada));
    }

    public boolean esCumpleanos() {
        return datos.tipo() == TipoEvento.CUMPLEANOS;
    }

    public Integer cuposDisponibles() {
        return datos.cuposTotales() == null ? null : datos.cuposTotales() - cuposOcupados;
    }

    // ---------- reglas ----------

    private static DatosEvento normalizar(DatosEvento d) {
        List<String> consideraciones = d.consideraciones().stream()
                .filter(Objects::nonNull).map(String::strip).filter(s -> !s.isEmpty()).toList();
        boolean cumple = d.tipo() == TipoEvento.CUMPLEANOS;
        return new DatosEvento(d.titulo().strip(), d.tipo(), d.inicio(), d.fin(), d.lugar().strip(),
                cumple ? 0 : d.precio(),
                cumple ? null : d.cuposTotales(),
                cumple ? null : d.limitePorCliente(),
                d.descripcion().strip(), consideraciones, vacioANull(d.imagenUrl()), d.publicado());
    }

    private static void validar(DatosEvento d) {
        if (d.fin() != null && !d.fin().isAfter(d.inicio())) {
            throw new ValidacionException("fechaHoraFin", "Debe ser posterior a la fecha y hora de inicio.");
        }
        if (d.tipo() == TipoEvento.CUMPLEANOS) {
            if (d.fin() == null) {
                throw new ValidacionException("fechaHoraFin", "Un cumpleaños necesita hora de término (bloque horario).");
            }
        } else {
            if (d.cuposTotales() == null || d.cuposTotales() < 1) {
                throw new ValidacionException("cuposTotales", "Debe haber al menos 1 cupo.");
            }
            if (d.precio() < 0) {
                throw new ValidacionException("precio", "El precio no puede ser negativo.");
            }
            if (d.limitePorCliente() != null && d.limitePorCliente() < 1) {
                throw new ValidacionException("limitePorUsuario", "Debe ser mayor que 0 o vacío (ilimitado).");
            }
        }
        if (d.consideraciones().size() > MAX_CONSIDERACIONES
                || d.consideraciones().stream().anyMatch(c -> c.length() > MAX_LARGO_CONSIDERACION)) {
            throw new ValidacionException("consideraciones", "Máximo " + MAX_CONSIDERACIONES + " puntos de hasta "
                    + MAX_LARGO_CONSIDERACION + " caracteres.");
        }
        if (d.imagenUrl() != null && (!d.imagenUrl().startsWith("https://") || d.imagenUrl().length() > 500)) {
            throw new ValidacionException("imagenUrl", "Debe ser una URL https:// de hasta 500 caracteres.");
        }
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    public Long id() { return id; }
    public DatosEvento datos() { return datos; }
    public int cuposOcupados() { return cuposOcupados; }
    public int version() { return version; }
    public Instant actualizadoEn() { return actualizadoEn; }
    public Instant eliminadoEn() { return eliminadoEn; }
}
