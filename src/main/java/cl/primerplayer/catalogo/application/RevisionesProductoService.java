package cl.primerplayer.catalogo.application;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.catalogo.api.RevisionDesactualizadaException;
import cl.primerplayer.catalogo.api.RevisionNoPendienteException;
import cl.primerplayer.catalogo.api.RevisionesProductoUseCase;
import cl.primerplayer.catalogo.application.port.out.ProductoRepositorio;
import cl.primerplayer.catalogo.application.port.out.RevisionRepositorio;
import cl.primerplayer.catalogo.application.port.out.RevisionRepositorio.Revision;
import cl.primerplayer.catalogo.domain.Producto;
import cl.primerplayer.shared.api.ConflictoVersionException;
import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import cl.primerplayer.shared.api.Rol;
import cl.primerplayer.shared.api.ValidacionException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class RevisionesProductoService implements RevisionesProductoUseCase {

    private final RevisionRepositorio revisiones;
    private final ProductoRepositorio productos;
    private final ReferenciasProducto referencias;
    private final java.time.Clock clock;

    // ---------- consultas ----------

    @Override
    @Transactional(readOnly = true)
    public Pagina<RevisionProducto> buscar(EstadoRevision estado, int page, int size) {
        Pagina<Revision> pagina = revisiones.buscar(estado, page, size);
        Map<Long, Producto> porId = productos.buscarPorIds(
                pagina.content().stream().map(Revision::productoId).distinct().toList());
        return pagina.map(r -> vista(r, porId.get(r.productoId())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RevisionProducto> deProducto(long productoId) {
        Producto producto = productos.buscarActivo(productoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El producto " + productoId + " no existe."));
        return revisiones.deProducto(productoId).stream().map(r -> vista(r, producto)).toList();
    }

    // ---------- comandos (solo ADMIN) ----------

    @Override
    @Transactional
    public RevisionProducto aprobar(long revisionId, long adminId) {
        Revision r = pendienteBloqueada(revisionId);
        Producto actual = productoDe(r);
        var ahora = clock.instant();

        switch (r.tipo()) {
            // Autorizar el alta es autorizar su publicación: queda a la venta.
            case ALTA -> productos.actualizar(actual.aprobar(ahora).conPublicacion(true, ahora), actual.version());
            case EDICION -> {
                if (actual.version() != r.versionBase()) {
                    throw new RevisionDesactualizadaException();
                }
                // Las reglas del rol del vendedor se validaron al proponer; aquí la categoría o una imagen pudieron
                // dejar de existir entre medio.
                referencias.validar(r.propuesto());
                productos.actualizar(actual.actualizar(r.propuesto(), Rol.ADMIN, ahora), actual.version());
            }
        }
        revisiones.resolver(r.id(), EstadoRevision.APROBADA, null, adminId, ahora);
        return recargar(r.id());
    }

    @Override
    @Transactional
    public RevisionProducto aprobar(long revisionId, CambiosAprobacion cambios, long adminId) {
        if (cambios == null) {
            return aprobar(revisionId, adminId);
        }
        Revision r = pendienteBloqueada(revisionId);
        Producto actual = productoDe(r);
        // El ADMIN corrigió sobre la versión que vio: si alguien cambió el producto entre medio, que vuelva a mirar.
        // (Una propuesta desactualizada no importa aquí: lo que queda es lo que el ADMIN dejó.)
        if (actual.version() != cambios.versionProducto()) {
            throw new ConflictoVersionException();
        }
        referencias.validar(cambios.datos());
        var ahora = clock.instant();

        Producto base = actual.aprobado() ? actual : actual.aprobar(ahora);   // ALTA: aprobada, ya puede publicarse
        Producto aplicado = base.actualizar(cambios.datos(), Rol.ADMIN, ahora);
        productos.actualizar(aplicado, actual.version());

        revisiones.resolver(r.id(), EstadoRevision.APROBADA, null, adminId, ahora);
        revisiones.guardarAprobados(r.id(), aplicado.datos());   // normalizados: lo que de verdad quedó
        return recargar(r.id());
    }

    @Override
    @Transactional
    public RevisionProducto rechazar(long revisionId, String motivo, long adminId) {
        if (motivo == null || motivo.isBlank()) {
            throw new ValidacionException("motivo", "El motivo del rechazo es obligatorio.");
        }
        Revision r = pendienteBloqueada(revisionId);
        Producto actual = productoDe(r);
        var ahora = clock.instant();
        if (r.tipo() == TipoRevision.ALTA) {
            productos.actualizar(actual.rechazar(ahora), actual.version());
        }
        // EDICION: la propuesta se descarta; el producto sigue con lo aprobado.
        revisiones.resolver(r.id(), EstadoRevision.RECHAZADA, motivo.strip(), adminId, ahora);
        return recargar(r.id());
    }

    // ---------- helpers ----------

    /** Bloquea la revisión: dos ADMIN que resuelven a la vez se serializan y el segundo ve que ya no está pendiente. */
    private Revision pendienteBloqueada(long revisionId) {
        Revision r = revisiones.bloquear(revisionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La revisión " + revisionId + " no existe."));
        if (r.estado() != EstadoRevision.PENDIENTE) {
            throw new RevisionNoPendienteException(r.estado().name());
        }
        return r;
    }

    private Producto productoDe(Revision r) {
        // Al eliminar un producto sus revisiones pendientes se descartan: una pendiente siempre tiene producto activo.
        return productos.buscarActivo(r.productoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("El producto " + r.productoId() + " no existe."));
    }

    private RevisionProducto recargar(long revisionId) {
        Revision r = revisiones.bloquear(revisionId).orElseThrow();
        return vista(r, productos.buscarPorId(r.productoId()).orElseThrow());
    }

    private static RevisionProducto vista(Revision r, Producto producto) {
        boolean desactualizada = r.tipo() == TipoRevision.EDICION && r.estado() == EstadoRevision.PENDIENTE
                && producto != null && producto.version() != r.versionBase();
        return new RevisionProducto(r.id(), r.productoId(), r.tipo(), r.estado(), producto == null ? null : ProductosAdmin.de(producto),
                r.propuesto(), r.aprobado(), desactualizada, r.motivoRechazo(), r.solicitadoPor(), r.solicitadoEn(),
                r.revisadoPor(), r.revisadoEn());
    }
}
