package cl.primerplayer.inventario.application.port.out;

/** Fila única de {@code config_inventario}. */
public interface ConfiguracionInventarioRepositorio {

    int umbralStockBajo();

    void guardarUmbralStockBajo(int umbral);
}
