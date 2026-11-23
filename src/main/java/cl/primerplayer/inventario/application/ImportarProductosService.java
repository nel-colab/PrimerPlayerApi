package cl.primerplayer.inventario.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import cl.primerplayer.inventario.api.ImportarProductosUseCase;
import cl.primerplayer.inventario.api.ImportarProductosUseCase.ErrorFila;
import cl.primerplayer.inventario.api.ImportarProductosUseCase.FilaImportacion;
import cl.primerplayer.inventario.api.ImportarProductosUseCase.ResultadoImportacion;
import cl.primerplayer.shared.api.ExcepcionNegocio;
import cl.primerplayer.shared.api.ValidacionException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class ImportarProductosService implements ImportarProductosUseCase {

    private final ProcesarFilaImportacion procesarFila;

    @Override
    public ResultadoImportacion importar(List<FilaImportacion> filas, Map<Integer, String> erroresLectura,
                                         long usuarioId) {
        int creados = 0;
        int actualizados = 0;
        List<ErrorFila> errores = new ArrayList<>();
        Map<Integer, String> erroresPorFila = erroresLectura == null ? Map.of() : erroresLectura;

        for (int i = 0; i < filas.size(); i++) {
            int numeroFila = i + 1;
            FilaImportacion fila = filas.get(i);
            String errorLectura = erroresPorFila.get(numeroFila);
            if (errorLectura != null) {
                errores.add(new ErrorFila(numeroFila, errorLectura));
                continue;
            }
            String error = validar(fila);
            if (error != null) {
                errores.add(new ErrorFila(numeroFila, error));
                continue;
            }
            try {
                if (procesarFila.procesar(fila, usuarioId) == ProcesarFilaImportacion.Resultado.CREADO) {
                    creados++;
                } else {
                    actualizados++;
                }
            } catch (ValidacionException | ExcepcionNegocio e) {
                errores.add(new ErrorFila(numeroFila, e.getMessage()));
            }
        }
        return new ResultadoImportacion(creados, actualizados, errores);
    }

    private static String validar(FilaImportacion fila) {
        if (fila.nombre() == null || fila.nombre().isBlank()) {
            return "El nombre es requerido";
        }
        if (fila.nombre().length() > 200) {
            return "El nombre no puede exceder 200 caracteres";
        }
        if (fila.precio() != null && fila.precio() < 0) {
            return "El precio no puede ser negativo";
        }
        if (fila.precio() != null && fila.precio() > 100_000_000) {
            return "El precio no puede exceder 100000000 CLP";
        }
        if (fila.stock() != null && fila.stock() < 0) {
            return "El stock no puede ser negativo";
        }
        if (fila.limitePorUsuario() != null && fila.limitePorUsuario() < 1) {
            return "El límite por usuario debe ser ≥ 1";
        }
        if (fila.limitePorUsuario() != null && fila.limitePorUsuario() > 999) {
            return "El límite por usuario no puede exceder 999";
        }
        if (excede(fila.descripcion(), 10_000) || excede(fila.categoria(), 60) || excede(fila.tipo(), 40)
                || excede(fila.idioma(), 30) || excede(fila.marca(), 60) || excede(fila.plataforma(), 30)
                || excede(fila.franquicia(), 60) || excede(fila.linea(), 80)) {
            return "Uno o más campos de texto exceden el largo permitido";
        }
        return null;
    }

    private static boolean excede(String valor, int maximo) {
        return valor != null && valor.length() > maximo;
    }
}
