package cl.primerplayer.inventario.api;

/** Puerto de entrada: configuración del inventario (solo ADMIN). */
public interface ConfiguracionInventarioUseCase {

    int umbralStockBajo();

    int cambiarUmbralStockBajo(int umbral);
}
