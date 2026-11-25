package cl.primerplayer.catalogo.infrastructure.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import cl.primerplayer.catalogo.api.CodigoBarrasEnUsoException;
import cl.primerplayer.shared.api.ConflictoVersionException;
import cl.primerplayer.catalogo.api.ConsultarProductosAdminUseCase.FiltroProductos;
import cl.primerplayer.catalogo.api.ConsultarProductosAdminUseCase.IndicadoresProductos;
import cl.primerplayer.catalogo.api.ConsultarProductosAdminUseCase.Orden;
import cl.primerplayer.catalogo.api.DatosProducto;
import cl.primerplayer.catalogo.api.DatosProducto.ImagenProducto;
import cl.primerplayer.catalogo.application.port.out.ProductoRepositorio;
import cl.primerplayer.catalogo.domain.Producto;
import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.ValidacionException;
import lombok.RequiredArgsConstructor;

/** Adaptador de salida: implementa {@link ProductoRepositorio} con JPA. */
@Component
@RequiredArgsConstructor
class ProductoPersistenceAdapter implements ProductoRepositorio {

    private final ProductoJpaRepository productos;
    private final ProductoImagenJpaRepository imagenes;
    private final CategoriaJpaRepository categorias;
    private final FranquiciaJpaRepository franquicias;
    private final ProductoFiltros filtros;
    private final ImagenesResueltas resueltas;

    @Override
    public Optional<Producto> buscarActivo(long id) {
        return productos.findByIdAndEliminadoEnIsNull(id).map(this::completo);
    }

    @Override
    public Optional<Producto> buscarPorId(long id) {
        return productos.findById(id).map(this::completo);
    }

    @Override
    public Map<Long, Producto> buscarPorIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return enLote(productos.findAllById(ids)).stream()
                .collect(Collectors.toMap(Producto::id, Function.identity()));
    }

    @Override
    public Optional<Producto> buscarPorCodigoBarras(String codigo) {
        return productos.findByCodigoBarrasAndEliminadoEnIsNull(codigo).map(this::completo);
    }

    @Override
    public Optional<Producto> buscarPorNombreExacto(String nombre) {
        return productos.findFirstByNombreIgnoreCaseAndEliminadoEnIsNullOrderByIdAsc(nombre).map(this::completo);
    }

    @Override
    public List<Producto> todosActivos() {
        return enLote(productos.findAllByEliminadoEnIsNullOrderByIdAsc());
    }

    @Override
    public long siguienteCorrelativoInterno() {
        return productos.siguienteCorrelativoInterno();
    }

    @Override
    public boolean codigoBarrasEnUso(String codigo) {
        return productos.existsByCodigoBarras(codigo);
    }

    @Override
    public Producto guardarNuevo(Producto p) {
        ProductoEntity e = new ProductoEntity();
        e.setCreadoEn(p.actualizadoEn());
        e.setFechaIngreso(p.fechaIngreso());
        copiar(p, e);
        try {
            productos.saveAndFlush(e);
        } catch (DataIntegrityViolationException ex) {
            throw traducir(ex, p.datos().codigoBarras());
        }
        reemplazarImagenes(e.getId(), p.datos().imagenes());
        return completo(e);   // URLs resueltas
    }

    @Override
    public Producto actualizar(Producto p, int versionEsperada) {
        ProductoEntity e = productos.findById(p.id()).orElseThrow(ConflictoVersionException::new);
        if (e.getVersion() != versionEsperada) {
            throw new ConflictoVersionException();
        }
        List<ImagenProducto> anteriores = imagenesDe(imagenes.findByProductoIdOrderByPosicion(p.id()));
        copiar(p, e);
        try {
            productos.saveAndFlush(e);   // @Version: si otro guardó entre medio, falla aquí
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ConflictoVersionException();
        } catch (DataIntegrityViolationException ex) {
            throw traducir(ex, p.datos().codigoBarras());
        }
        if (!anteriores.equals(p.datos().imagenes())) {
            reemplazarImagenes(e.getId(), p.datos().imagenes());
        }
        return completo(e);   // URLs resueltas
    }

    @Override
    public Pagina<Producto> buscar(FiltroProductos f, int umbralStockBajo) {
        Sort sort = orden(f.orden());
        Page<ProductoEntity> page = productos.findAll(filtros.de(f, umbralStockBajo),
                PageRequest.of(f.page(), f.size(), sort));
        return Pagina.de(enLote(page.getContent()), f.page(), f.size(), page.getTotalElements());
    }

    @Override
    public IndicadoresProductos indicadores(int umbralStockBajo) {
        var fila = productos.indicadores(umbralStockBajo);
        return new IndicadoresProductos(fila.getTotal(), fila.getStockBajo(), fila.getAgotados());
    }

    // ---------- mapeo ----------

    private void copiar(Producto p, ProductoEntity e) {
        DatosProducto d = p.datos();
        e.setNombre(d.nombre());
        e.setDescripcion(d.descripcion());
        e.setPrecio(d.precio());
        e.setCategoria(categorias.findBySlug(d.categoria())
                .orElseThrow(() -> new ValidacionException("categoria", "La categoría no existe.")));
        e.setTipo(d.tipo());
        e.setIdioma(d.idioma());
        e.setMarca(d.marca());
        e.setPlataforma(d.plataforma());
        e.setFranquicia(d.franquicia() == null ? null : franquicias.findByNombreIgnoreCase(d.franquicia())
                .orElseThrow(() -> new ValidacionException("franquicia", "La franquicia no existe.")));
        e.setLinea(d.linea());
        e.setCodigoBarras(d.codigoBarras());
        e.setLimitePorCliente(d.limitePorCliente());
        e.setPublicado(d.publicado());
        e.setAprobacion(p.aprobacion());
        e.setActualizadoEn(p.actualizadoEn());
        e.setEliminadoEn(p.eliminadoEn());
    }

    /**
     * Borra con el EntityManager (no con un DELETE masivo, que dejaría entidades obsoletas en el contexto) y hace
     * flush antes de insertar: así las nuevas filas no chocan con la PK ni con la portada única.
     */
    private void reemplazarImagenes(long productoId, List<ImagenProducto> nuevas) {
        imagenes.deleteAll(imagenes.findByProductoIdOrderByPosicion(productoId));
        imagenes.flush();
        List<ProductoImagenEntity> filas = new ArrayList<>();
        for (int i = 0; i < nuevas.size(); i++) {
            ImagenProducto img = nuevas.get(i);
            filas.add(new ProductoImagenEntity(productoId, (short) i, img.archivoId(), img.url(), img.portada()));
        }
        imagenes.saveAllAndFlush(filas);
    }

    private List<ImagenProducto> imagenesDe(List<ProductoImagenEntity> filas) {
        return resueltas.de(filas);
    }

    /** Un producto con sus imágenes y si tiene una edición pendiente. */
    private Producto completo(ProductoEntity e) {
        boolean cambioPendiente = !productos.conEdicionPendiente(List.of(e.getId())).isEmpty();
        return aDominio(e, imagenesDe(imagenes.findByProductoIdOrderByPosicion(e.getId())), cambioPendiente);
    }

    /** Varios productos: imágenes (URLs de archivos resueltas) y ediciones pendientes en una consulta cada una. */
    private List<Producto> enLote(List<ProductoEntity> filas) {
        if (filas.isEmpty()) {
            return List.of();
        }
        List<Long> ids = filas.stream().map(ProductoEntity::getId).toList();
        Map<Long, List<ImagenProducto>> imgs = resueltas.porProducto(imagenes.findByProductoIdIn(ids));
        Set<Long> conCambio = new HashSet<>(productos.conEdicionPendiente(ids));
        return filas.stream()
                .map(e -> aDominio(e, imgs.getOrDefault(e.getId(), List.of()), conCambio.contains(e.getId())))
                .toList();
    }

    private static Producto aDominio(ProductoEntity e, List<ImagenProducto> imagenes, boolean cambioPendiente) {
        DatosProducto d = new DatosProducto(e.getNombre(), e.getDescripcion(), e.getPrecio(),
                e.getCategoria().getSlug(), e.getTipo(), e.getIdioma(), e.getMarca(), e.getPlataforma(),
                e.getFranquicia() == null ? null : e.getFranquicia().getNombre(), e.getLinea(), e.getCodigoBarras(),
                e.getLimitePorCliente(), e.isPublicado(), imagenes);
        return Producto.reconstituir(e.getId(), d, e.getStock(), e.getPopularidad(), e.getFechaIngreso(),
                e.getVersion(), e.getActualizadoEn(), e.getEliminadoEn(), e.getAprobacion(), cambioPendiente);
    }

    private static RuntimeException traducir(DataIntegrityViolationException ex, String codigo) {
        String msg = String.valueOf(ex.getMostSpecificCause().getMessage());
        return msg.contains("uq_producto_codigo_barras") ? new CodigoBarrasEnUsoException(codigo) : ex;
    }

    private static Sort orden(Orden orden) {
        if (orden == null) {
            return Sort.by(Sort.Direction.DESC, "id");
        }
        String campo = switch (orden.campo()) {
            case ID -> "id";
            case NOMBRE -> "nombre";
            case PRECIO -> "precio";
            case STOCK -> "stock";
            case FECHA -> "fechaIngreso";
        };
        Sort.Direction dir = orden.ascendente() ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, campo).and(Sort.by(Sort.Direction.DESC, "id"));   // desempate estable
    }
}
