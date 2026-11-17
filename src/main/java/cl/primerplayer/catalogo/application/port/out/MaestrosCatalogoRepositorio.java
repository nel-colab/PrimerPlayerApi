package cl.primerplayer.catalogo.application.port.out;

import java.util.List;
import java.util.Optional;

import cl.primerplayer.catalogo.api.ConsultarCategoriasUseCase.NodoCategoria;

/** Puerto de salida: categorías y franquicias (datos maestros de solo lectura). */
public interface MaestrosCatalogoRepositorio {

    /** {@code Optional.empty()} si el slug no existe; si existe, indica si es hoja. */
    Optional<Boolean> categoriaEsHoja(String slug);

    boolean existeFranquicia(String nombre);

    List<NodoCategoria> arbolCategorias();

    List<String> franquicias();
}
