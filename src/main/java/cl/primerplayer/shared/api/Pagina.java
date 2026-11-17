package cl.primerplayer.shared.api;

import java.util.List;
import java.util.function.Function;

/** Página de resultados con el formato común de la API: {@code {content, page, size, totalElements, totalPages}}. */
public record Pagina<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> Pagina<T> de(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        return new Pagina<>(List.copyOf(content), page, size, totalElements, totalPages);
    }

    public <R> Pagina<R> map(Function<? super T, ? extends R> f) {
        return new Pagina<>(content.stream().<R>map(f).toList(), page, size, totalElements, totalPages);
    }
}
