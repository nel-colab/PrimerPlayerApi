package cl.primerplayer.catalogo.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import cl.primerplayer.catalogo.api.CodigoBarrasInmutableException;
import cl.primerplayer.catalogo.api.DatosProducto;
import cl.primerplayer.catalogo.api.DatosProducto.ImagenProducto;
import cl.primerplayer.catalogo.api.EstadoAprobacion;
import cl.primerplayer.catalogo.api.ProductoNoAprobadoException;
import cl.primerplayer.shared.api.LimiteSoloAdminException;
import cl.primerplayer.shared.api.Rol;
import cl.primerplayer.shared.api.ValidacionException;

/**
 * Agregado Producto. Modelo puro (sin JPA ni Spring). Inmutable: cada operación devuelve una copia.
 * El {@code stock} es de solo lectura aquí: lo escribe exclusivamente el módulo inventario.
 *
 * <p>Aprobación: solo un producto {@link EstadoAprobacion#APROBADO} puede estar publicado. Un VENDEDOR crea
 * productos {@link EstadoAprobacion#PENDIENTE}; un ADMIN los aprueba ({@link #aprobar}) o rechaza.
 */
public final class Producto {

    public static final int MAX_IMAGENES = 5;

    private final Long id;
    private final DatosProducto datos;
    private final int stock;
    private final int popularidad;
    private final LocalDate fechaIngreso;
    private final int version;
    private final Instant actualizadoEn;
    private final Instant eliminadoEn;
    private final EstadoAprobacion aprobacion;
    private final boolean cambioPendiente;

    private Producto(Long id, DatosProducto datos, int stock, int popularidad, LocalDate fechaIngreso, int version,
                     Instant actualizadoEn, Instant eliminadoEn, EstadoAprobacion aprobacion,
                     boolean cambioPendiente) {
        if (datos.publicado() && aprobacion != EstadoAprobacion.APROBADO) {
            throw new ProductoNoAprobadoException();   // la base tiene el mismo CHECK como red final
        }
        this.id = id;
        this.datos = Objects.requireNonNull(datos);
        this.stock = stock;
        this.popularidad = popularidad;
        this.fechaIngreso = fechaIngreso;
        this.version = version;
        this.actualizadoEn = actualizadoEn;
        this.eliminadoEn = eliminadoEn;
        this.aprobacion = Objects.requireNonNull(aprobacion);
        this.cambioPendiente = cambioPendiente;
    }

    /**
     * Producto nuevo: stock 0 (el stock inicial entra por un ajuste de inventario).
     *
     * @param requiereAprobacion lo crea un VENDEDOR: nace pendiente y sin publicar, pida lo que pida
     */
    public static Producto crear(DatosProducto datos, LocalDate hoy, Instant ahora, boolean requiereAprobacion) {
        DatosProducto limpios = normalizar(datos);
        validar(limpios);
        if (requiereAprobacion) {
            return new Producto(null, conPublicado(limpios, false), 0, 0, hoy, 0, ahora, null,
                    EstadoAprobacion.PENDIENTE, false);
        }
        return new Producto(null, limpios, 0, 0, hoy, 0, ahora, null, EstadoAprobacion.APROBADO, false);
    }

    public static Producto reconstituir(Long id, DatosProducto datos, int stock, int popularidad,
                                        LocalDate fechaIngreso, int version, Instant actualizadoEn,
                                        Instant eliminadoEn, EstadoAprobacion aprobacion, boolean cambioPendiente) {
        return new Producto(id, datos, stock, popularidad, fechaIngreso, version, actualizadoEn, eliminadoEn,
                aprobacion, cambioPendiente);
    }

    /**
     * Reemplaza los datos editables aplicando las reglas de cambio.
     *
     * <ul>
     *   <li>Código de barras: cualquier rol interno lo define al crear; después, solo ADMIN puede agregarlo
     *       (si no tenía) o cambiarlo. Para VENDEDOR es inmutable.</li>
     *   <li>Límite por cliente ya configurado: solo ADMIN lo cambia. Si estaba vacío (ilimitado), cualquier rol
     *       interno puede definirlo.</li>
     * </ul>
     */
    public Producto actualizar(DatosProducto nuevos, Rol rol, Instant ahora) {
        DatosProducto limpios = normalizar(nuevos);
        validar(limpios);

        if (!Objects.equals(datos.codigoBarras(), limpios.codigoBarras()) && rol != Rol.ADMIN) {
            throw new CodigoBarrasInmutableException();
        }
        Integer limiteActual = datos.limitePorCliente();
        if (limiteActual != null && !limiteActual.equals(limpios.limitePorCliente()) && rol != Rol.ADMIN) {
            throw new LimiteSoloAdminException();
        }
        return new Producto(id, limpios, stock, popularidad, fechaIngreso, version, ahora, eliminadoEn, aprobacion,
                cambioPendiente);
    }

    /**
     * Valida un cambio sin aplicarlo, con las mismas reglas que {@link #actualizar} para el rol dado. Devuelve los
     * datos normalizados, listos para guardarse como propuesta. No exige que el producto esté aprobado para publicar:
     * eso lo decide quien aprueba.
     */
    public DatosProducto validarPropuesta(DatosProducto nuevos, Rol rol) {
        DatosProducto propuestos = actualizar(conPublicado(nuevos, false), rol, actualizadoEn).datos();
        return conPublicado(propuestos, nuevos.publicado());
    }

    /**
     * @throws ProductoNoAprobadoException si se publica un producto no aprobado (un ADMIN llama antes a
     *                                     {@link #aprobar})
     */
    public Producto conPublicacion(boolean publicado, Instant ahora) {
        return new Producto(id, conPublicado(datos, publicado), stock, popularidad, fechaIngreso, version, ahora,
                eliminadoEn, aprobacion, cambioPendiente);
    }

    /** Autoriza el alta. No publica: quien aprueba lo publica después con {@link #conPublicacion}. */
    public Producto aprobar(Instant ahora) {
        return new Producto(id, datos, stock, popularidad, fechaIngreso, version, ahora, eliminadoEn,
                EstadoAprobacion.APROBADO, cambioPendiente);
    }

    /** Alta rechazada: queda sin publicar hasta que el vendedor la corrija y vuelva a revisión. */
    public Producto rechazar(Instant ahora) {
        return new Producto(id, conPublicado(datos, false), stock, popularidad, fechaIngreso, version, ahora,
                eliminadoEn, EstadoAprobacion.RECHAZADO, cambioPendiente);
    }

    /** Un alta rechazada que el vendedor corrigió vuelve a esperar aprobación (sin publicar). */
    public Producto reenviarARevision(Instant ahora) {
        return new Producto(id, conPublicado(datos, false), stock, popularidad, fechaIngreso, version, ahora,
                eliminadoEn, EstadoAprobacion.PENDIENTE, cambioPendiente);
    }

    public Producto eliminar(Instant ahora) {
        return new Producto(id, datos, stock, popularidad, fechaIngreso, version, ahora, ahora, aprobacion,
                cambioPendiente);
    }

    public boolean aprobado() {
        return aprobacion == EstadoAprobacion.APROBADO;
    }

    private static DatosProducto conPublicado(DatosProducto d, boolean publicado) {
        return new DatosProducto(d.nombre(), d.descripcion(), d.precio(), d.categoria(), d.tipo(), d.idioma(),
                d.marca(), d.plataforma(), d.franquicia(), d.linea(), d.codigoBarras(), d.limitePorCliente(),
                publicado, d.imagenes());
    }

    private static DatosProducto normalizar(DatosProducto d) {
        return new DatosProducto(d.nombre().strip(), vacioANull(d.descripcion()), d.precio(), d.categoria().strip(),
                vacioANull(d.tipo()), vacioANull(d.idioma()), vacioANull(d.marca()), vacioANull(d.plataforma()),
                vacioANull(d.franquicia()), vacioANull(d.linea()), vacioANull(d.codigoBarras()),
                d.limitePorCliente(), d.publicado(), d.imagenes());
    }

    private static void validar(DatosProducto d) {
        if (d.precio() < 0) {
            throw new ValidacionException("precio", "El precio no puede ser negativo.");
        }
        if (d.limitePorCliente() != null && d.limitePorCliente() < 1) {
            throw new ValidacionException("limitePorUsuario", "El límite debe ser mayor que 0 o vacío (ilimitado).");
        }
        if (d.codigoBarras() != null && !CodigoBarras.esValido(d.codigoBarras())) {
            throw new ValidacionException("codigoBarras",
                    "Código de barras inválido: debe ser EAN-8, UPC-A o EAN-13 con dígito verificador correcto.");
        }
        validarImagenes(d.imagenes());
    }

    private static void validarImagenes(List<ImagenProducto> imagenes) {
        if (imagenes.size() > MAX_IMAGENES) {
            throw new ValidacionException("imagenes", "Máximo " + MAX_IMAGENES + " imágenes por producto.");
        }
        if (imagenes.isEmpty()) {
            return;
        }
        long portadas = imagenes.stream().filter(ImagenProducto::portada).count();
        if (portadas != 1) {
            throw new ValidacionException("imagenes", "Debe haber exactamente una imagen de portada.");
        }
        for (ImagenProducto img : imagenes) {
            if (img.archivoId() != null) {
                continue;   // imagen subida: su existencia y que sea pública se verifican en la aplicación
            }
            // El servidor nunca descarga estas URLs (evita SSRF): solo valida el esquema y el largo.
            if (img.url() == null || !img.url().startsWith("https://") || img.url().length() > 500) {
                throw new ValidacionException("imagenes",
                        "Cada imagen debe ser un archivo subido o una URL https:// de hasta 500 caracteres.");
            }
        }
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    public Long id() { return id; }
    public DatosProducto datos() { return datos; }
    public int stock() { return stock; }
    public int popularidad() { return popularidad; }
    public LocalDate fechaIngreso() { return fechaIngreso; }
    public int version() { return version; }
    public Instant actualizadoEn() { return actualizadoEn; }
    public Instant eliminadoEn() { return eliminadoEn; }
    public boolean eliminado() { return eliminadoEn != null; }
    public EstadoAprobacion aprobacion() { return aprobacion; }
    public boolean cambioPendiente() { return cambioPendiente; }
}
