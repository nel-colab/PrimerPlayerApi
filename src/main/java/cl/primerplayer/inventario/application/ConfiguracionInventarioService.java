package cl.primerplayer.inventario.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.catalogo.api.UmbralStockBajo;
import cl.primerplayer.inventario.api.ConfiguracionInventarioUseCase;
import cl.primerplayer.inventario.application.port.out.ConfiguracionInventarioRepositorio;
import cl.primerplayer.shared.api.ValidacionException;
import lombok.RequiredArgsConstructor;

/**
 * Configuración del inventario. Implementa también el SPI {@link UmbralStockBajo} de catálogo, que así obtiene
 * el umbral sin depender de inventario.
 */
@Service
@RequiredArgsConstructor
class ConfiguracionInventarioService implements ConfiguracionInventarioUseCase, UmbralStockBajo {

    static final int MAX_UMBRAL = 9_999;

    private final ConfiguracionInventarioRepositorio configuracion;

    @Override
    @Transactional(readOnly = true)
    public int umbralStockBajo() {
        return configuracion.umbralStockBajo();
    }

    @Override
    @Transactional
    public int cambiarUmbralStockBajo(int umbral) {
        if (umbral < 0 || umbral > MAX_UMBRAL) {
            throw new ValidacionException("umbralStockBajo", "Debe estar entre 0 y " + MAX_UMBRAL + ".");
        }
        configuracion.guardarUmbralStockBajo(umbral);
        return umbral;
    }
}
