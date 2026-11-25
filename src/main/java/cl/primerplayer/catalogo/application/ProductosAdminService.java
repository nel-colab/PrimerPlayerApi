package cl.primerplayer.catalogo.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.catalogo.api.AdministrarProductosUseCase;
import cl.primerplayer.shared.api.ConflictoVersionException;
import cl.primerplayer.catalogo.api.ConsultarProductosAdminUseCase;
import cl.primerplayer.catalogo.api.DatosProducto;
import cl.primerplayer.catalogo.api.ProductoAdmin;
import cl.primerplayer.catalogo.api.UmbralStockBajo;
import cl.primerplayer.catalogo.api.ProductoNoAprobadoException;
import cl.primerplayer.catalogo.api.RevisionesProductoUseCase.EstadoRevision;
import cl.primerplayer.catalogo.api.RevisionesProductoUseCase.TipoRevision;
import cl.primerplayer.catalogo.application.port.out.ProductoRepositorio;
import cl.primerplayer.catalogo.application.port.out.RevisionRepositorio;
import cl.primerplayer.catalogo.application.port.out.RevisionRepositorio.Revision;
import cl.primerplayer.catalogo.domain.CodigoBarras;
import cl.primerplayer.catalogo.domain.Producto;
import cl.primerplayer.catalogo.events.ProductoEnRevision;
import cl.primerplayer.shared.api.Pagina;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class ProductosAdminService implements AdministrarProductosUseCase, ConsultarProductosAdminUseCase {

    /** Zona de los cortes diarios: la fecha de ingreso es la fecha local de la tienda. */
    private static final java.time.ZoneId ZONA_TIENDA = java.time.ZoneId.of("America/Santiago");

    private final ProductoRepositorio productos;
    private final RevisionRepositorio revisiones;
    private final ReferenciasProducto referencias;
    private final UmbralStockBajo umbral;
    private final Clock clock;
    private final ApplicationEventPublisher eventos;

    // ---------- comandos ----------

    @Override
    @Transactional
    public ProductoAdmin crear(DatosProducto datos, Actor actor) {
        referencias.validar(datos);
        var ahora = clock.instant();
        boolean requiereAprobacion = !actor.esAdmin();
        Producto nuevo = productos.guardarNuevo(
                Producto.crear(datos, LocalDate.ofInstant(ahora, ZONA_TIENDA), ahora, requiereAprobacion));
        if (requiereAprobacion) {
            long rev = revisiones.crear(nuevo.id(), TipoRevision.ALTA, null, nuevo.version(), actor.usuarioId(), ahora);
            avisarAlta(rev, nuevo, actor, ahora);
        }
        return ProductosAdmin.de(nuevo);
    }

    @Override
    @Transactional
    public ResultadoCambio actualizar(long id, int versionEsperada, DatosProducto datos, Actor actor) {
        Producto actual = activo(id);
        if (actual.version() != versionEsperada) {
            throw new ConflictoVersionException();
        }
        referencias.validar(datos);
        var ahora = clock.instant();

        if (actor.esAdmin()) {
            // Un ADMIN que publica un producto no aprobado lo está autorizando.
            Producto base = datos.publicado() && !actual.aprobado() ? actual.aprobar(ahora) : actual;
            Producto guardado = productos.actualizar(base.actualizar(datos, actor.rol(), ahora), versionEsperada);
            if (guardado.aprobado() && !actual.aprobado()) {
                cerrarAltaPendiente(id, EstadoRevision.APROBADA, actor, ahora);
            }
            return new ResultadoCambio(ProductosAdmin.de(guardado), false);
        }

        if (!actual.aprobado()) {
            // Alta aún no autorizada: no está a la venta, así que el vendedor la corrige directo (sin publicar).
            Producto cambiado = actual.actualizar(ProductosAdmin.conPublicado(datos, false), actor.rol(), ahora)
                    .reenviarARevision(ahora);
            Producto guardado = productos.actualizar(cambiado, versionEsperada);
            if (revisiones.pendienteDe(id).isEmpty()) {   // estaba rechazada: vuelve a la bandeja
                long rev = revisiones.crear(id, TipoRevision.ALTA, null, guardado.version(), actor.usuarioId(), ahora);
                avisarAlta(rev, guardado, actor, ahora);
            }
            return new ResultadoCambio(ProductosAdmin.de(guardado), false);
        }

        // Producto aprobado (a la venta): el cambio queda como propuesta; sigue vendiéndose con lo aprobado.
        DatosProducto propuestos = actual.validarPropuesta(datos, actor.rol());
        return proponer(actual, propuestos, actor, ahora);
    }

    @Override
    @Transactional
    public ResultadoCambio cambiarPublicacion(long id, boolean publicado, Actor actor) {
        Producto actual = activo(id);
        var ahora = clock.instant();
        if (!publicado || actual.datos().publicado() == publicado) {
            // Ocultar nunca espera aprobación: sacar algo de la venta es la dirección segura.
            Producto guardado = actual.datos().publicado() == publicado ? actual
                    : productos.actualizar(actual.conPublicacion(false, ahora), actual.version());
            return new ResultadoCambio(ProductosAdmin.de(guardado), false);
        }

        if (actor.esAdmin()) {
            Producto base = actual.aprobado() ? actual : actual.aprobar(ahora);
            Producto guardado = productos.actualizar(base.conPublicacion(true, ahora), actual.version());
            if (!actual.aprobado()) {
                cerrarAltaPendiente(id, EstadoRevision.APROBADA, actor, ahora);
            }
            return new ResultadoCambio(ProductosAdmin.de(guardado), false);
        }

        if (!actual.aprobado()) {
            throw new ProductoNoAprobadoException();   // se publicará cuando un ADMIN apruebe el alta
        }
        // Republicar un producto aprobado: propuesta sobre lo que ya estaba propuesto (si había) o sobre lo actual.
        DatosProducto base = revisiones.pendienteDe(id).map(Revision::propuesto).filter(Objects::nonNull)
                .orElse(actual.datos());
        return proponer(actual, ProductosAdmin.conPublicado(base, true), actor, ahora);
    }

    @Override
    @Transactional
    public void eliminar(long id, Actor actor) {
        Producto actual = activo(id);
        var ahora = clock.instant();
        productos.actualizar(actual.eliminar(ahora), actual.version());
        revisiones.pendienteDe(id).ifPresent(r ->
                revisiones.resolver(r.id(), EstadoRevision.DESCARTADA, null, actor.usuarioId(), ahora));
    }

    @Override
    @Transactional   // no readOnly: PostgreSQL no permite nextval() en una transacción de solo lectura
    public String generarCodigoBarras() {
        // El correlativo nunca se repite; solo puede chocar con un código escrito a mano en el rango 200 o con los
        // datos de demostración. Se salta los ocupados (en la práctica, ninguno o muy pocos).
        for (int intento = 0; intento < 100; intento++) {
            String codigo = CodigoBarras.eanInterno(productos.siguienteCorrelativoInterno());
            if (!productos.codigoBarrasEnUso(codigo)) {
                return codigo;
            }
        }
        throw new IllegalStateException("No se encontró un código de barras interno libre en 100 intentos.");
    }

    /**
     * Guarda (o reemplaza) la propuesta pendiente del producto. Si lo propuesto es igual a lo aprobado, no hay nada
     * que revisar: se descarta la propuesta que hubiera.
     */
    private ResultadoCambio proponer(Producto actual, DatosProducto propuestos, Actor actor, Instant ahora) {
        long id = actual.id();
        var pendiente = revisiones.pendienteDe(id);
        if (ProductosAdmin.mismosDatos(propuestos, actual.datos())) {
            pendiente.ifPresent(r ->
                    revisiones.resolver(r.id(), EstadoRevision.DESCARTADA, null, actor.usuarioId(), ahora));
            return new ResultadoCambio(ProductosAdmin.de(activo(id)), false);
        }
        long rev;
        if (pendiente.isPresent()) {
            rev = pendiente.get().id();
            revisiones.reemplazarPropuesta(rev, propuestos, actual.version(), actor.usuarioId(), ahora);
        } else {
            rev = revisiones.crear(id, TipoRevision.EDICION, propuestos, actual.version(), actor.usuarioId(), ahora);
        }
        // Solo un cambio de precio amerita avisar por correo; el resto espera en la bandeja.
        // notificaciones envía una sola vez por revisión y precio propuesto.
        if (propuestos.precio() != actual.datos().precio()) {
            eventos.publishEvent(new ProductoEnRevision(rev, id, actual.datos().nombre(), false,
                    actual.datos().precio(), propuestos.precio(), actor.usuarioId(), ahora));
        }
        return new ResultadoCambio(ProductosAdmin.de(activo(id)), true);
    }

    /** Producto nuevo (o alta rechazada que el vendedor corrigió) esperando aprobación: aviso a los ADMIN. */
    private void avisarAlta(long revisionId, Producto producto, Actor actor, Instant ahora) {
        eventos.publishEvent(new ProductoEnRevision(revisionId, producto.id(), producto.datos().nombre(), true, null,
                producto.datos().precio(), actor.usuarioId(), ahora));
    }

    private void cerrarAltaPendiente(long productoId, EstadoRevision estado, Actor actor, Instant ahora) {
        revisiones.pendienteDe(productoId)
                .filter(r -> r.tipo() == TipoRevision.ALTA)
                .ifPresent(r -> revisiones.resolver(r.id(), estado, null, actor.usuarioId(), ahora));
    }

    // ---------- consultas ----------

    @Override
    @Transactional(readOnly = true)
    public ProductoAdmin obtener(long id) {
        return ProductosAdmin.de(activo(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductoAdmin> porCodigoBarrasActivo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        return productos.buscarPorCodigoBarras(codigo.strip()).map(ProductosAdmin::de);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductoAdmin> porNombreExacto(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return Optional.empty();
        }
        return productos.buscarPorNombreExacto(nombre.strip()).map(ProductosAdmin::de);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoAdmin> todosActivos() {
        return productos.todosActivos().stream().map(ProductosAdmin::de).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductoAdmin porCodigoBarras(String codigo) {
        String limpio = codigo == null ? "" : codigo.strip();
        return productos.buscarPorCodigoBarras(limpio)
                .filter(p -> p.datos().publicado())
                .map(ProductosAdmin::de)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No hay un producto a la venta con el código de barras " + limpio + "."));
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<ProductoAdmin> buscar(FiltroProductos filtro) {
        return productos.buscar(filtro, umbral.umbralStockBajo()).map(ProductosAdmin::de);
    }

    @Override
    @Transactional(readOnly = true)
    public IndicadoresProductos indicadores() {
        return productos.indicadores(umbral.umbralStockBajo());
    }

    // ---------- reglas que necesitan la base ----------

    private Producto activo(long id) {
        return productos.buscarActivo(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El producto " + id + " no existe."));
    }
}
