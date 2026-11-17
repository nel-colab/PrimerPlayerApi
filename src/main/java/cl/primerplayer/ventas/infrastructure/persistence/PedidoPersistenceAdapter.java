package cl.primerplayer.ventas.infrastructure.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Component;

import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.ventas.api.CanalPedido;
import cl.primerplayer.ventas.api.ConsultarPedidosUseCase.FiltroPedidos;
import cl.primerplayer.ventas.api.EstadoPedido;
import cl.primerplayer.ventas.application.port.out.PedidoRepositorio;
import cl.primerplayer.ventas.domain.Pedido;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/** Adaptador de salida: implementa {@link PedidoRepositorio} con JPA. */
@Component
@RequiredArgsConstructor
class PedidoPersistenceAdapter implements PedidoRepositorio {

    /** Más recientes primero; desempate estable por id. */
    private static final Sort RECIENTES = Sort.by(Sort.Direction.DESC, "creadoEn").and(Sort.by(Sort.Direction.DESC, "id"));

    private final PedidoJpaRepository pedidos;
    private final PedidoItemJpaRepository items;

    @Override
    public Pagina<Pedido> delCliente(long clienteId, int page, int size) {
        Specification<PedidoEntity> delCliente = (root, query, cb) -> cb.equal(root.get("clienteId"), clienteId);
        return pagina(pedidos.findAll(delCliente, PageRequest.of(page, size, RECIENTES)), page, size);
    }

    @Override
    public Optional<Pedido> porCodigo(String codigo) {
        return pedidos.findByCodigoIgnoreCase(codigo.strip())
                .map(e -> aDominio(e, items.findByPedidoIdOrderById(e.getId())));
    }

    @Override
    public Optional<Pedido> porId(long id) {
        return pedidos.findById(id).map(e -> aDominio(e, items.findByPedidoIdOrderById(e.getId())));
    }

    @Override
    public Pagina<Pedido> buscar(FiltroPedidos f) {
        Specification<PedidoEntity> spec = (root, query, cb) -> {
            List<Predicate> y = new ArrayList<>();
            if (f.desde() != null) {
                y.add(cb.greaterThanOrEqualTo(root.get("creadoEn"), f.desde()));
            }
            if (f.hasta() != null) {
                y.add(cb.lessThan(root.get("creadoEn"), f.hasta()));
            }
            if (f.canal() != null) {
                y.add(cb.equal(root.get("canal"), f.canal().valor()));
            }
            if (!f.estados().isEmpty()) {
                y.add(root.get("estado").in(f.estados().stream().map(Enum::name).toList()));
            }
            if (f.vendedorId() != null) {
                y.add(cb.equal(root.get("vendedorId"), f.vendedorId()));
            }
            if (f.codigo() != null) {
                String patron = "%" + f.codigo().toUpperCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                y.add(cb.like(cb.upper(root.get("codigo")), patron, '\\'));
            }
            return cb.and(y.toArray(Predicate[]::new));
        };
        return pagina(pedidos.findAll(spec, PageRequest.of(f.page(), f.size(), RECIENTES)), f.page(), f.size());
    }

    @Override
    public List<Pedido> validosEntre(Instant desde, Instant hasta) {
        Specification<PedidoEntity> spec = (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("creadoEn"), desde),
                cb.lessThan(root.get("creadoEn"), hasta),
                root.get("estado").in(List.of("PAGADO", "LISTO_RETIRO", "ENTREGADO")));
        List<PedidoEntity> filas = pedidos.findAll(spec, Sort.by("creadoEn", "id"));
        List<Long> ids = filas.stream().map(PedidoEntity::getId).toList();
        Map<Long, List<PedidoItemEntity>> porPedido = ids.isEmpty() ? Map.of()
                : items.findByPedidoIdIn(ids).stream()
                        .sorted(Comparator.comparingLong(PedidoItemEntity::getId))
                        .collect(Collectors.groupingBy(PedidoItemEntity::getPedidoId));
        return filas.stream().map(e -> aDominio(e, porPedido.getOrDefault(e.getId(), List.of()))).toList();
    }

    @Override
    public boolean cambiarEstado(long id, EstadoPedido esperado, EstadoPedido nuevo, Instant ahora) {
        return pedidos.cambiarEstado(id, esperado.name(), nuevo.name(), ahora) == 1;
    }

    // ---------- mapeo ----------

    /** Carga los ítems de toda la página en una sola consulta. */
    private Pagina<Pedido> pagina(Page<PedidoEntity> page, int numero, int size) {
        List<Long> ids = page.getContent().stream().map(PedidoEntity::getId).toList();
        Map<Long, List<PedidoItemEntity>> porPedido = ids.isEmpty() ? Map.of()
                : items.findByPedidoIdIn(ids).stream()
                        .sorted(Comparator.comparingLong(PedidoItemEntity::getId))
                        .collect(Collectors.groupingBy(PedidoItemEntity::getPedidoId));
        List<Pedido> contenido = page.getContent().stream()
                .map(e -> aDominio(e, porPedido.getOrDefault(e.getId(), List.of())))
                .toList();
        return Pagina.de(contenido, numero, size, page.getTotalElements());
    }

    private static Pedido aDominio(PedidoEntity e, List<PedidoItemEntity> filas) {
        return new Pedido(e.getId(), e.getCodigo(), e.getClienteId(), e.getVendedorId(), CanalPedido.de(e.getCanal()),
                EstadoPedido.valueOf(e.getEstado()), e.getTotal(), e.getCreadoEn(), e.getPagadoEn(),
                e.getActualizadoEn(),
                filas.stream().map(i -> new Pedido.Item(i.getTipo(),
                        "product".equals(i.getTipo()) ? i.getProductoId() : i.getEventoId(),
                        i.getNombre(), i.getCantidad(), i.getPrecioUnitario(), i.getSubtotal())).toList());
    }
}

interface PedidoJpaRepository extends JpaRepository<PedidoEntity, Long>, JpaSpecificationExecutor<PedidoEntity> {

    Optional<PedidoEntity> findByCodigoIgnoreCase(String codigo);

    /** Cambio de estado atómico: 0 filas si otro proceso lo cambió antes. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE PedidoEntity p SET p.estado = :nuevo, p.actualizadoEn = :ahora
        WHERE p.id = :id AND p.estado = :esperado
        """)
    int cambiarEstado(long id, String esperado, String nuevo, Instant ahora);
}

interface PedidoItemJpaRepository extends JpaRepository<PedidoItemEntity, Long> {

    List<PedidoItemEntity> findByPedidoIdOrderById(long pedidoId);

    List<PedidoItemEntity> findByPedidoIdIn(Collection<Long> pedidoIds);
}
