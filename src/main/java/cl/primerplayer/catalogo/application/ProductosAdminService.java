package cl.primerplayer.catalogo.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.catalogo.api.AdministrarProductosUseCase;
import cl.primerplayer.shared.api.ConflictoVersionException;
import cl.primerplayer.catalogo.api.ConsultarProductosAdminUseCase;
import cl.primerplayer.catalogo.api.DatosProducto;
import cl.primerplayer.catalogo.api.ProductoAdmin;
import cl.primerplayer.catalogo.api.UmbralStockBajo;
import cl.primerplayer.catalogo.application.port.out.MaestrosCatalogoRepositorio;
import cl.primerplayer.catalogo.application.port.out.ProductoRepositorio;
import cl.primerplayer.catalogo.domain.Producto;
import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import cl.primerplayer.shared.api.Rol;
import cl.primerplayer.shared.api.ValidacionException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class ProductosAdminService implements AdministrarProductosUseCase, ConsultarProductosAdminUseCase {

    /** Zona de los cortes diarios: la fecha de ingreso es la fecha local de la tienda. */
    private static final java.time.ZoneId ZONA_TIENDA = java.time.ZoneId.of("America/Santiago");

    private final ProductoRepositorio productos;
    private final MaestrosCatalogoRepositorio maestros;
    private final UmbralStockBajo umbral;
    private final cl.primerplayer.media.api.ArchivosUseCase media;
    private final Clock clock;

    // ---------- comandos ----------

    @Override
    @Transactional
    public ProductoAdmin crear(DatosProducto datos) {
        validarReferencias(datos);
        var ahora = clock.instant();
        Producto nuevo = Producto.crear(datos, LocalDate.ofInstant(ahora, ZONA_TIENDA), ahora);
        return ProductosAdmin.de(productos.guardarNuevo(nuevo));
    }

    @Override
    @Transactional
    public ProductoAdmin actualizar(long id, int versionEsperada, DatosProducto datos, Rol rol) {
        Producto actual = activo(id);
        if (actual.version() != versionEsperada) {
            throw new ConflictoVersionException();
        }
        validarReferencias(datos);
        Producto cambiado = actual.actualizar(datos, rol, clock.instant());
        return ProductosAdmin.de(productos.actualizar(cambiado, versionEsperada));
    }

    @Override
    @Transactional
    public ProductoAdmin cambiarPublicacion(long id, boolean publicado) {
        Producto actual = activo(id);
        return ProductosAdmin.de(productos.actualizar(actual.conPublicacion(publicado, clock.instant()), actual.version()));
    }

    @Override
    @Transactional
    public void eliminar(long id) {
        Producto actual = activo(id);
        productos.actualizar(actual.eliminar(clock.instant()), actual.version());
    }

    // ---------- consultas ----------

    @Override
    @Transactional(readOnly = true)
    public ProductoAdmin obtener(long id) {
        return ProductosAdmin.de(activo(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductoAdmin> porCodigoBarrasActivo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        return productos.buscarPorCodigoBarras(codigo.strip()).map(ProductosAdmin::de);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductoAdmin> porNombreExacto(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return Optional.empty();
        }
        return productos.buscarPorNombreExacto(nombre.strip()).map(ProductosAdmin::de);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoAdmin> todosActivos() {
        return productos.todosActivos().stream().map(ProductosAdmin::de).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductoAdmin porCodigoBarras(String codigo) {
        String limpio = codigo == null ? "" : codigo.strip();
        return productos.buscarPorCodigoBarras(limpio)
                .filter(p -> p.datos().publicado())
                .map(ProductosAdmin::de)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No hay un producto a la venta con el código de barras " + limpio + "."));
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<ProductoAdmin> buscar(FiltroProductos filtro) {
        return productos.buscar(filtro, umbral.umbralStockBajo()).map(ProductosAdmin::de);
    }

    @Override
    @Transactional(readOnly = true)
    public IndicadoresProductos indicadores() {
        return productos.indicadores(umbral.umbralStockBajo());
    }

    // ---------- reglas que necesitan la base ----------

    private Producto activo(long id) {
        return productos.buscarActivo(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El producto " + id + " no existe."));
    }

    private void validarReferencias(DatosProducto datos) {
        Boolean hoja = maestros.categoriaEsHoja(datos.categoria().strip())
                .orElseThrow(() -> new ValidacionException("categoria", "La categoría no existe."));
        if (!hoja) {
            throw new ValidacionException("categoria", "La categoría debe ser una hoja, no una agrupadora.");
        }
        if (datos.franquicia() != null && !datos.franquicia().isBlank()
                && !maestros.existeFranquicia(datos.franquicia().strip())) {
            throw new ValidacionException("franquicia", "La franquicia no existe.");
        }
        // Una imagen subida debe existir y ser pública: un comprobante privado nunca puede quedar como foto.
        for (var img : datos.imagenes()) {
            if (img.archivoId() != null && !media.esImagenPublica(img.archivoId())) {
                throw new ValidacionException("imagenes", "La imagen subida no existe o no es una imagen pública.");
            }
        }
    }
}
