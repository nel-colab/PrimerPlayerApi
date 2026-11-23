package cl.primerplayer.inventario.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import cl.primerplayer.inventario.application.port.out.ConfiguracionInventarioRepositorio;
import cl.primerplayer.inventario.application.port.out.MovimientoStockRepositorio;
import cl.primerplayer.inventario.application.port.out.StockRepositorio;
import cl.primerplayer.inventario.domain.MovimientoStock;
import cl.primerplayer.shared.api.Pagina;
import lombok.RequiredArgsConstructor;

/** Adaptadores de salida de inventario. */
final class InventarioPersistenceAdapters {
    private InventarioPersistenceAdapters() {}
}

@Component
@RequiredArgsConstructor
class StockPersistenceAdapter implements StockRepositorio {

    private final StockJpaRepository jpa;

    @Override public Optional<Integer> descontar(long productoId, int cantidad) { return jpa.descontar(productoId, cantidad); }
    @Override public Optional<Integer> ajustar(long productoId, int delta) { return jpa.ajustar(productoId, delta); }
    @Override public Optional<CambioStock> reemplazar(long productoId, int stock) {
        return jpa.reemplazar(productoId, stock)
                .map(fila -> new CambioStock(fila.getAnterior(), fila.getActual()));
    }
    @Override public Optional<Integer> reponer(long productoId, int cantidad) { return jpa.reponer(productoId, cantidad); }
    @Override public Optional<Integer> stockActivo(long productoId) { return jpa.stockActivo(productoId); }
    @Override public boolean existe(long productoId) { return jpa.existsById(productoId); }
}

interface MovimientoStockJpaRepository extends JpaRepository<MovimientoStockEntity, Long> {
    Page<MovimientoStockEntity> findByProductoId(long productoId, org.springframework.data.domain.Pageable pageable);
}

@Component
@RequiredArgsConstructor
class MovimientoStockPersistenceAdapter implements MovimientoStockRepositorio {

    private final MovimientoStockJpaRepository jpa;

    @Override
    public void guardar(MovimientoStock m) {
        jpa.save(new MovimientoStockEntity(null, m.productoId(), m.tipo(), m.cantidad(), m.stockResultante(),
                m.referencia(), m.motivo(), m.usuarioId(), m.creadoEn()));
    }

    @Override
    public Pagina<MovimientoStock> deProducto(long productoId, int page, int size) {
        var pagina = jpa.findByProductoId(productoId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "creadoEn", "id")));
        var contenido = pagina.getContent().stream()
                .map(e -> new MovimientoStock(e.getId(), e.getProductoId(), e.getTipo(), e.getCantidad(),
                        e.getStockResultante(), e.getReferencia(), e.getMotivo(), e.getUsuarioId(), e.getCreadoEn()))
                .toList();
        return Pagina.de(contenido, page, size, pagina.getTotalElements());
    }
}

interface ConfigInventarioJpaRepository extends JpaRepository<ConfigInventarioEntity, Short> {
}

@Component
@RequiredArgsConstructor
class ConfiguracionInventarioPersistenceAdapter implements ConfiguracionInventarioRepositorio {

    private final ConfigInventarioJpaRepository jpa;

    @Override
    public int umbralStockBajo() {
        return fila().getUmbralStockBajo();
    }

    @Override
    public void guardarUmbralStockBajo(int umbral) {
        fila().setUmbralStockBajo(umbral);   // entidad administrada: se guarda al commit
    }

    private ConfigInventarioEntity fila() {
        return jpa.findById(ConfigInventarioEntity.ID_UNICO)
                .orElseThrow(() -> new IllegalStateException("Falta la fila única de config_inventario (V2)"));
    }
}
