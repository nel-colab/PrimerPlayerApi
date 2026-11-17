package cl.primerplayer.catalogo.infrastructure.persistence;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import cl.primerplayer.catalogo.api.ConsultarProductosAdminUseCase.FiltroProductos;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/** Traduce el filtro del mantenedor a una Specification JPA (solo campos de la lista blanca). */
@Component
@RequiredArgsConstructor
class ProductoFiltros {

    private final CategoriaJpaRepository categorias;

    Specification<ProductoEntity> de(FiltroProductos f, int umbral) {
        Set<Short> categoriaIds = idsDeCategorias(f.categorias());
        return (root, query, cb) -> {
            List<Predicate> y = new ArrayList<>();
            y.add(cb.isNull(root.get("eliminadoEn")));

            if (f.q() != null && !f.q().isBlank()) {
                String q = f.q().strip();
                String patron = "%" + escaparLike(sinTildes(q)) + "%";
                // Usa el índice trigram ix_producto_nombre_trgm: f_unaccent(lower(nombre))
                List<Predicate> o = new ArrayList<>();
                o.add(cb.like(cb.function("f_unaccent", String.class, cb.lower(root.get("nombre"))), patron, '\\'));
                o.add(cb.equal(root.get("codigoBarras"), q));
                if (q.matches("\\d{1,18}")) {
                    o.add(cb.equal(root.get("id"), Long.parseLong(q)));
                }
                y.add(cb.or(o.toArray(Predicate[]::new)));
            }

            if (!f.categorias().isEmpty()) {
                y.add(categoriaIds.isEmpty() ? cb.disjunction() : root.get("categoria").get("id").in(categoriaIds));
            }

            if (!f.estados().isEmpty()) {
                List<Predicate> o = new ArrayList<>();
                for (var estado : f.estados()) {
                    o.add(switch (estado) {
                        case PUBLICADO -> cb.isTrue(root.get("publicado"));
                        case OCULTO -> cb.isFalse(root.get("publicado"));
                        case AGOTADO -> cb.equal(root.get("stock"), 0);
                        case STOCK_BAJO -> cb.between(root.get("stock"), 1, umbral);
                    });
                }
                y.add(cb.or(o.toArray(Predicate[]::new)));
            }
            return cb.and(y.toArray(Predicate[]::new));
        };
    }

    /** Una agrupadora incluye todas sus hojas descendientes. Slugs desconocidos se ignoran. */
    Set<Short> idsDeCategorias(List<String> slugs) {
        if (slugs.isEmpty()) {
            return Set.of();
        }
        List<CategoriaEntity> todas = categorias.findAll();   // tabla chica (~40 filas)
        Map<Short, List<CategoriaEntity>> hijos = todas.stream().filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(CategoriaEntity::getParentId));
        Set<Short> ids = new HashSet<>();
        todas.stream().filter(c -> slugs.contains(c.getSlug())).forEach(c -> agregarHojas(c, hijos, ids));
        return ids;
    }

    private static void agregarHojas(CategoriaEntity c, Map<Short, List<CategoriaEntity>> hijos, Set<Short> ids) {
        if (c.isEsHoja()) {
            ids.add(c.getId());
        }
        hijos.getOrDefault(c.getId(), List.of()).forEach(h -> agregarHojas(h, hijos, ids));
    }

    static String sinTildes(String s) {
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    static String escaparLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
