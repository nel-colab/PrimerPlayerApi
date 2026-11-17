package cl.primerplayer.clientes.api;

import java.time.Instant;
import java.util.List;

import cl.primerplayer.catalogo.api.ProductoPublico;

/** Puerto de entrada: direcciones, favoritos y alertas de stock del cliente (siempre del usuario de la sesión). */
public interface CuentaClienteUseCase {

    int MAX_DIRECCIONES = 5;

    // ---------- direcciones ----------

    List<Direccion> direcciones(long usuarioId);

    /** La primera dirección queda como principal. Marcar otra como principal desmarca la anterior. */
    Direccion crearDireccion(long usuarioId, DatosDireccion datos);

    /** @throws cl.primerplayer.shared.api.RecursoNoEncontradoException si no existe o es de otro cliente */
    Direccion actualizarDireccion(long usuarioId, long direccionId, DatosDireccion datos);

    void eliminarDireccion(long usuarioId, long direccionId);

    // ---------- favoritos ----------

    /** Solo productos visibles (los ocultos o eliminados no aparecen). */
    List<Favorito> favoritos(long usuarioId);

    /** Idempotente. @throws cl.primerplayer.shared.api.RecursoNoEncontradoException si el producto no está a la venta */
    void agregarFavorito(long usuarioId, long productoId);

    void quitarFavorito(long usuarioId, long productoId);

    // ---------- alertas de stock ----------

    List<Alerta> alertas(long usuarioId);

    /** Idempotente; si ya se había notificado, la vuelve a dejar pendiente. */
    void crearAlerta(long usuarioId, long productoId);

    void quitarAlerta(long usuarioId, long productoId);

    record DatosDireccion(String alias, String calle, String comuna, String region, String referencia, boolean principal) {}

    record Direccion(long id, String alias, String calle, String comuna, String region, String referencia, boolean principal) {}

    record Favorito(ProductoPublico producto, Instant agregadoEn) {}

    /** @param notificadaEn {@code null} = pendiente (avisará cuando vuelva el stock) */
    record Alerta(ProductoPublico producto, Instant creadaEn, Instant notificadaEn) {}
}
