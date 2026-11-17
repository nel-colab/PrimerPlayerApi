package cl.primerplayer.catalogo.api;

import java.util.List;

/** Puerto de entrada: datos maestros públicos para filtros y formularios. */
public interface ConsultarCategoriasUseCase {

    /** Árbol ordenado por {@code orden}. */
    List<NodoCategoria> arbol();

    /** Nombres de franquicias en orden alfabético. */
    List<String> franquicias();

    record NodoCategoria(String slug, String titulo, boolean hoja, List<NodoCategoria> hijos) {}
}
