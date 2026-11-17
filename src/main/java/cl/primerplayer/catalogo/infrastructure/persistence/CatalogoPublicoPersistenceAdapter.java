package cl.primerplayer.catalogo.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import cl.primerplayer.catalogo.api.ConsultarCatalogoPublicoUseCase.Facetas;
import cl.primerplayer.catalogo.api.ConsultarCatalogoPublicoUseCase.FiltroCatalogo;
import cl.primerplayer.catalogo.api.ConsultarCatalogoPublicoUseCase.OrdenCatalogo;
import cl.primerplayer.catalogo.api.ConsultarCatalogoPublicoUseCase.RangoPrecio;
import cl.primerplayer.catalogo.api.ConsultarCatalogoPublicoUseCase.ResultadoCatalogo;
import cl.primerplayer.catalogo.api.ConsultarCatalogoPublicoUseCase.ValorFaceta;
import cl.primerplayer.catalogo.api.DatosProducto.ImagenProducto;
import cl.primerplayer.catalogo.api.ProductoPublico;
import cl.primerplayer.catalogo.application.port.out.CatalogoPublicoRepositorio;
import cl.primerplayer.shared.api.Pagina;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;

/** Adaptador de salida: catálogo público con JPA Criteria (filtros de lista blanca y facetas). */
@Component
@RequiredArgsConstructor
class CatalogoPublicoPersistenceAdapter implements CatalogoPublicoRepositorio {

    /** Slug que significa "todas las categorías". */
    private static final String TODOS = "todos";

    private final ProductoJpaRepository productos;
    private final ProductoImagenJpaRepository imagenes;
    private final ProductoFiltros filtros;
    private final ImagenesResueltas resueltas;
    private final EntityManager em;

    /** Dimensiones filtrables por valor exacto (y con faceta). */
    private enum Dimension {
        TIPO, IDIOMA, MARCA, PLATAFORMA, FRANQUICIA, LINEA, PRECIO;

        Set<String> valores(FiltroCatalogo f) {
            return switch (this) {
                case TIPO -> f.tipo();
                case IDIOMA -> f.idioma();
                case MARCA -> f.marca();
                case PLATAFORMA -> f.plataforma();
                case FRANQUICIA -> f.franquicia();
                case LINEA -> f.linea();
                case PRECIO -> Set.of();
            };
        }

        Path<String> campo(Root<ProductoEntity> root) {
            return switch (this) {
                case TIPO -> root.get("tipo");
                case IDIOMA -> root.get("idioma");
                case MARCA -> root.get("marca");
                case PLATAFORMA -> root.get("plataforma");
                case FRANQUICIA -> root.join("franquicia", JoinType.INNER).get("nombre");
                case LINEA -> root.get("linea");
                case PRECIO -> throw new IllegalStateException("El precio es un rango, no un valor");
            };
        }
    }

    @Override
    public ResultadoCatalogo buscar(FiltroCatalogo f) {
        Set<Short> categorias = categorias(f.categoria());
        Page<ProductoEntity> page = productos.findAll(spec(f, categorias, null),
                PageRequest.of(f.page(), f.size(), orden(f.orden())));
        Pagina<ProductoPublico> pagina = Pagina.de(aPublicos(page.getContent()), f.page(), f.size(),
                page.getTotalElements());

        Facetas facetas = new Facetas(
                faceta(f, categorias, Dimension.TIPO),
                faceta(f, categorias, Dimension.IDIOMA),
                faceta(f, categorias, Dimension.MARCA),
                faceta(f, categorias, Dimension.PLATAFORMA),
                faceta(f, categorias, Dimension.FRANQUICIA),
                faceta(f, categorias, Dimension.LINEA),
                rangoPrecio(f, categorias));
        return new ResultadoCatalogo(pagina, facetas);
    }

    @Override
    public List<ProductoPublico> masPopulares(int limite) {
        Page<ProductoEntity> page = productos.findAll(visibles(),
                PageRequest.of(0, limite, orden(OrdenCatalogo.RECOMENDADOS)));
        return aPublicos(page.getContent());
    }

    @Override
    public Optional<ProductoPublico> visible(long id) {
        Specification<ProductoEntity> porId = (root, query, cb) -> cb.equal(root.get("id"), id);
        return productos.findOne(visibles().and(porId)).map(e -> aPublicos(List.of(e)).getFirst());
    }

    @Override
    public List<ProductoPublico> visibles(java.util.Collection<Long> ids) {
        Specification<ProductoEntity> porIds = (root, query, cb) -> root.get("id").in(ids);
        return aPublicos(productos.findAll(visibles().and(porIds)));
    }

    @Override
    public List<ProductoPublico> relacionados(long id, int limite) {
        return aPublicos(productos.relacionados(id, limite));
    }

    // ---------- filtros ----------

    /** Publicados y no eliminados: la base de toda consulta pública. */
    private static Specification<ProductoEntity> visibles() {
        return (root, query, cb) -> cb.and(cb.isTrue(root.get("publicado")), cb.isNull(root.get("eliminadoEn")));
    }

    /**
     * @param categorias {@code null} = sin filtro de categoría; vacío = el slug no existe (sin resultados)
     * @param sin        dimensión cuyo filtro se omite (para calcular su propia faceta); {@code null} = ninguna
     */
    private Specification<ProductoEntity> spec(FiltroCatalogo f, Set<Short> categorias, Dimension sin) {
        return (root, query, cb) -> {
            List<Predicate> y = new ArrayList<>();
            y.add(cb.isTrue(root.get("publicado")));
            y.add(cb.isNull(root.get("eliminadoEn")));

            if (categorias != null) {
                y.add(categorias.isEmpty() ? cb.disjunction() : root.get("categoria").get("id").in(categorias));
            }
            if (f.q() != null) {
                // Usa el índice trigram ix_producto_nombre_trgm: f_unaccent(lower(nombre))
                String patron = "%" + ProductoFiltros.escaparLike(ProductoFiltros.sinTildes(f.q())) + "%";
                y.add(cb.like(cb.function("f_unaccent", String.class, cb.lower(root.get("nombre"))), patron, '\\'));
            }
            if (f.enStock()) {
                y.add(cb.greaterThan(root.get("stock"), 0));
            }
            if (sin != Dimension.PRECIO) {
                if (f.precioMin() != null) {
                    y.add(cb.greaterThanOrEqualTo(root.get("precio"), f.precioMin()));
                }
                if (f.precioMax() != null) {
                    y.add(cb.lessThanOrEqualTo(root.get("precio"), f.precioMax()));
                }
            }
            for (Dimension d : Dimension.values()) {
                if (d != Dimension.PRECIO && d != sin && !d.valores(f).isEmpty()) {
                    y.add(d.campo(root).in(d.valores(f)));
                }
            }
            return cb.and(y.toArray(Predicate[]::new));
        };
    }

    /** Una agrupadora incluye sus hojas; {@code todos} o {@code null} no filtran. */
    private Set<Short> categorias(String slug) {
        if (slug == null || TODOS.equals(slug)) {
            return null;
        }
        return filtros.idsDeCategorias(List.of(slug));
    }

    // ---------- facetas ----------

    /** Valores con su cantidad sobre el resultado filtrado SIN el filtro de esta misma faceta. */
    private List<ValorFaceta> faceta(FiltroCatalogo f, Set<Short> categorias, Dimension d) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<ProductoEntity> root = cq.from(ProductoEntity.class);
        Path<String> campo = d.campo(root);
        Expression<Long> cantidad = cb.count(root);
        cq.multiselect(campo, cantidad)
                .where(spec(f, categorias, d).toPredicate(root, cq, cb), cb.isNotNull(campo))
                .groupBy(campo)
                .orderBy(cb.desc(cantidad), cb.asc(campo));
        return em.createQuery(cq).getResultList().stream()
                .map(t -> new ValorFaceta(t.get(0, String.class), t.get(1, Long.class)))
                .toList();
    }

    private RangoPrecio rangoPrecio(FiltroCatalogo f, Set<Short> categorias) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<ProductoEntity> root = cq.from(ProductoEntity.class);
        Path<Long> precio = root.get("precio");
        cq.multiselect(cb.min(precio), cb.max(precio))
                .where(spec(f, categorias, Dimension.PRECIO).toPredicate(root, cq, cb));
        Tuple t = em.createQuery(cq).getSingleResult();
        return new RangoPrecio(t.get(0, Long.class), t.get(1, Long.class));
    }

    // ---------- orden y mapeo ----------

    private static Sort orden(OrdenCatalogo orden) {
        Sort principal = switch (orden) {
            case RECOMENDADOS -> Sort.by(Sort.Direction.DESC, "popularidad");
            case RECIENTES -> Sort.by(Sort.Direction.DESC, "fechaIngreso");
            case PRECIO_ASC -> Sort.by(Sort.Direction.ASC, "precio");
            case PRECIO_DESC -> Sort.by(Sort.Direction.DESC, "precio");
        };
        return principal.and(Sort.by(Sort.Direction.DESC, "id"));   // desempate estable entre páginas
    }

    /** Carga las imágenes de toda la página en una sola consulta. */
    private List<ProductoPublico> aPublicos(List<ProductoEntity> entidades) {
        if (entidades.isEmpty()) {
            return List.of();
        }
        List<Long> ids = entidades.stream().map(ProductoEntity::getId).toList();
        Map<Long, List<ImagenProducto>> imgs = resueltas.porProducto(imagenes.findByProductoIdIn(ids));
        return entidades.stream().map(e -> aPublico(e, imgs.getOrDefault(e.getId(), List.of()))).toList();
    }

    private static ProductoPublico aPublico(ProductoEntity e, List<ImagenProducto> imagenes) {
        return new ProductoPublico(e.getId(), e.getNombre(), e.getDescripcion(), e.getPrecio(),
                e.getCategoria().getSlug(), e.getTipo(), e.getIdioma(), e.getMarca(), e.getPlataforma(),
                e.getFranquicia() == null ? null : e.getFranquicia().getNombre(), e.getLinea(),
                e.getLimitePorCliente(), e.getStock(), e.getPopularidad(), e.getFechaIngreso(),
                imagenes.stream().filter(i -> i.url() != null).toList());
    }
}
