package cl.primerplayer.catalogo.api;

import java.util.List;
import java.util.Set;

import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.ValidacionException;

/** Puerto de entrada: catálogo de la tienda pública. Solo productos publicados y no eliminados. */
public interface ConsultarCatalogoPublicoUseCase {

    int MAX_PAGE_SIZE = 100;
    int MAX_DESTACADOS = 24;
    int MAX_RELACIONADOS = 12;

    /** Página de productos + facetas para los filtros de la tienda. */
    ResultadoCatalogo buscar(FiltroCatalogo filtro);

    /** Los más populares. @param limite 1..{@value #MAX_DESTACADOS} */
    List<ProductoPublico> destacados(int limite);

    /** @throws cl.primerplayer.shared.api.RecursoNoEncontradoException si no existe, está oculto o eliminado */
    ProductoPublico obtener(long id);

    /** Varios productos en una consulta (carrito, checkout). Los ocultos, eliminados o inexistentes no aparecen. */
    java.util.Map<Long, ProductoPublico> visibles(java.util.Collection<Long> ids);

    /**
     * Misma categoría primero y luego misma franquicia; con stock antes que agotados.
     *
     * @param limite 1..{@value #MAX_RELACIONADOS}
     * @throws cl.primerplayer.shared.api.RecursoNoEncontradoException si el producto base no es visible
     */
    List<ProductoPublico> relacionados(long id, int limite);

    /**
     * @param categoria slug hoja o agrupadora ({@code todos} o {@code null} = sin filtro; slug desconocido = sin resultados)
     * @param q         texto en el nombre, sin importar tildes ni mayúsculas
     * @param enStock   solo productos con stock &gt; 0
     * @param tipo      valores exactos; dentro de una faceta se combinan con OR y entre facetas con AND
     */
    record FiltroCatalogo(String categoria, String q, boolean enStock, Long precioMin, Long precioMax,
                          Set<String> tipo, Set<String> idioma, Set<String> marca, Set<String> plataforma,
                          Set<String> franquicia, Set<String> linea, OrdenCatalogo orden, int page, int size) {

        public FiltroCatalogo {
            categoria = vacioANull(categoria);
            q = vacioANull(q);
            tipo = limpio(tipo);
            idioma = limpio(idioma);
            marca = limpio(marca);
            plataforma = limpio(plataforma);
            franquicia = limpio(franquicia);
            linea = limpio(linea);
            orden = orden == null ? OrdenCatalogo.RECOMENDADOS : orden;
            if (page < 0) {
                throw new ValidacionException("page", "Debe ser 0 o mayor.");
            }
            if (size < 1 || size > MAX_PAGE_SIZE) {
                throw new ValidacionException("size", "Debe estar entre 1 y " + MAX_PAGE_SIZE + ".");
            }
            if ((precioMin != null && precioMin < 0) || (precioMax != null && precioMax < 0)) {
                throw new ValidacionException("precioMin", "Los precios no pueden ser negativos.");
            }
            if (precioMin != null && precioMax != null && precioMin > precioMax) {
                throw new ValidacionException("precioMin", "precioMin no puede ser mayor que precioMax.");
            }
        }

        /** Sin filtros: todo lo publicado, orden recomendado. */
        public static FiltroCatalogo todos(int page, int size) {
            return new FiltroCatalogo(null, null, false, null, null, null, null, null, null, null, null, null, page, size);
        }

        private static String vacioANull(String s) {
            return s == null || s.isBlank() ? null : s.strip();
        }

        private static Set<String> limpio(Set<String> valores) {
            if (valores == null) {
                return Set.of();
            }
            return valores.stream().filter(v -> v != null && !v.isBlank()).map(String::strip)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    /** Orden de la tienda (lista blanca). Desempate estable por id descendente. */
    enum OrdenCatalogo {
        RECOMENDADOS("recomendados"), RECIENTES("recientes"), PRECIO_ASC("precio-asc"), PRECIO_DESC("precio-desc");

        private final String parametro;

        OrdenCatalogo(String parametro) {
            this.parametro = parametro;
        }

        public String parametro() {
            return parametro;
        }

        /** @throws ValidacionException si no está en la lista blanca */
        public static OrdenCatalogo deParametro(String valor) {
            if (valor == null || valor.isBlank()) {
                return RECOMENDADOS;
            }
            for (OrdenCatalogo o : values()) {
                if (o.parametro.equalsIgnoreCase(valor.strip())) {
                    return o;
                }
            }
            throw new ValidacionException("sort", "Use recomendados, recientes, precio-asc o precio-desc.");
        }
    }

    record ResultadoCatalogo(Pagina<ProductoPublico> pagina, Facetas facetas) {}

    /** Cada faceta se calcula sobre el resultado filtrado SIN su propio filtro (para poder ampliar la selección). */
    record Facetas(List<ValorFaceta> tipo, List<ValorFaceta> idioma, List<ValorFaceta> marca,
                   List<ValorFaceta> plataforma, List<ValorFaceta> franquicia, List<ValorFaceta> linea,
                   RangoPrecio precio) {}

    record ValorFaceta(String valor, long cantidad) {}

    /** {@code min}/{@code max} nulos si no hay resultados. */
    record RangoPrecio(Long min, Long max) {}
}
