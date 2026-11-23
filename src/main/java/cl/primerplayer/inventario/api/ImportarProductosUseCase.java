package cl.primerplayer.inventario.api;

import java.util.List;
import java.util.Map;

/** Importación por filas de productos y stock, conservando el detalle de errores sin abortar el lote. */
public interface ImportarProductosUseCase {

    ResultadoImportacion importar(List<FilaImportacion> filas, Map<Integer, String> erroresLectura, long usuarioId);

    record FilaImportacion(
            String nombre,
            String codigoBarras,
            Long precio,
            Integer stock,
            String categoria,
            String descripcion,
            Boolean publicado,
            Integer limitePorUsuario,
            String franquicia,
            String marca,
            String tipo,
            String plataforma,
            String idioma,
            String linea) {}

    record ResultadoImportacion(int creados, int actualizados, List<ErrorFila> errores) {
        public ResultadoImportacion {
            errores = List.copyOf(errores);
        }
    }

    record ErrorFila(int fila, String mensaje) {}
}
