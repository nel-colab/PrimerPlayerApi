package cl.primerplayer.clientes.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.catalogo.api.ConsultarCatalogoPublicoUseCase;
import cl.primerplayer.catalogo.api.ProductoPublico;
import cl.primerplayer.clientes.api.CuentaClienteUseCase;
import cl.primerplayer.clientes.events.AlertaStockDisparada;
import cl.primerplayer.clientes.infrastructure.ClientesJdbcRepositorio;
import cl.primerplayer.clientes.infrastructure.ClientesJdbcRepositorio.Marca;
import cl.primerplayer.identidad.api.ConsultarUsuarioUseCase;
import cl.primerplayer.inventario.events.StockRepuesto;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import cl.primerplayer.shared.api.ValidacionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
class CuentaClienteService implements CuentaClienteUseCase {

    private final ClientesJdbcRepositorio repo;
    private final ConsultarCatalogoPublicoUseCase catalogo;
    private final ConsultarUsuarioUseCase usuarios;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    // ---------- direcciones ----------

    @Override
    @Transactional(readOnly = true)
    public List<Direccion> direcciones(long usuarioId) {
        return repo.direcciones(usuarioId);
    }

    @Override
    @Transactional
    public Direccion crearDireccion(long usuarioId, DatosDireccion datos) {
        DatosDireccion d = validar(datos);
        int existentes = repo.contarDirecciones(usuarioId);
        if (existentes >= MAX_DIRECCIONES) {
            throw new ValidacionException("direcciones", "Máximo " + MAX_DIRECCIONES + " direcciones.");
        }
        boolean principal = d.principal() || existentes == 0;
        if (principal) {
            repo.desmarcarPrincipal(usuarioId);
        }
        long id = repo.insertarDireccion(usuarioId, conPrincipal(d, principal));
        return repo.direccion(usuarioId, id).orElseThrow();
    }

    @Override
    @Transactional
    public Direccion actualizarDireccion(long usuarioId, long direccionId, DatosDireccion datos) {
        DatosDireccion d = validar(datos);
        Direccion actual = repo.direccion(usuarioId, direccionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La dirección no existe."));
        if (d.principal() && !actual.principal()) {
            repo.desmarcarPrincipal(usuarioId);
        }
        repo.actualizarDireccion(usuarioId, direccionId, d);
        repo.asegurarPrincipal(usuarioId);
        return repo.direccion(usuarioId, direccionId).orElseThrow();
    }

    @Override
    @Transactional
    public void eliminarDireccion(long usuarioId, long direccionId) {
        if (!repo.eliminarDireccion(usuarioId, direccionId)) {
            throw new RecursoNoEncontradoException("La dirección no existe.");
        }
        repo.asegurarPrincipal(usuarioId);
    }

    // ---------- favoritos ----------

    @Override
    @Transactional(readOnly = true)
    public List<Favorito> favoritos(long usuarioId) {
        List<Marca> marcas = repo.favoritos(usuarioId);
        Map<Long, ProductoPublico> productos = catalogo.visibles(marcas.stream().map(Marca::productoId).toList());
        return marcas.stream().filter(m -> productos.containsKey(m.productoId()))
                .map(m -> new Favorito(productos.get(m.productoId()), m.creadaEn())).toList();
    }

    @Override
    @Transactional
    public void agregarFavorito(long usuarioId, long productoId) {
        visible(productoId);
        repo.agregarFavorito(usuarioId, productoId, clock.instant());
    }

    @Override
    @Transactional
    public void quitarFavorito(long usuarioId, long productoId) {
        repo.quitarFavorito(usuarioId, productoId);
    }

    // ---------- alertas de stock ----------

    @Override
    @Transactional(readOnly = true)
    public List<Alerta> alertas(long usuarioId) {
        List<Marca> marcas = repo.alertas(usuarioId);
        Map<Long, ProductoPublico> productos = catalogo.visibles(marcas.stream().map(Marca::productoId).toList());
        return marcas.stream().filter(m -> productos.containsKey(m.productoId()))
                .map(m -> new Alerta(productos.get(m.productoId()), m.creadaEn(), m.notificadaEn())).toList();
    }

    @Override
    @Transactional
    public void crearAlerta(long usuarioId, long productoId) {
        visible(productoId);
        repo.crearAlerta(usuarioId, productoId, clock.instant());
    }

    @Override
    @Transactional
    public void quitarAlerta(long usuarioId, long productoId) {
        repo.quitarAlerta(usuarioId, productoId);
    }

    /**
     * Stock de 0 a más de 0: marca las alertas pendientes y publica un aviso por cliente (notificaciones envía el
     * correo después del commit). Si el producto no está visible, las alertas siguen pendientes.
     */
    @ApplicationModuleListener
    void alReponerseStock(StockRepuesto e) {
        ProductoPublico p = catalogo.visibles(List.of(e.productoId())).get(e.productoId());
        if (p == null) {
            return;
        }
        List<Long> avisar = repo.marcarNotificadas(e.productoId(), clock.instant());
        if (avisar.isEmpty()) {
            return;
        }
        usuarios.buscarPorIds(avisar).values().forEach(u ->
                eventos.publishEvent(new AlertaStockDisparada(u.id(), u.email(), u.nombre(), p.id(), p.nombre())));
        log.info("Alertas de stock disparadas: producto={} clientes={}", p.id(), avisar.size());
    }

    // ---------- reglas ----------

    private void visible(long productoId) {
        if (!catalogo.visibles(List.of(productoId)).containsKey(productoId)) {
            throw new RecursoNoEncontradoException("El producto no existe o no está a la venta.");
        }
    }

    private static DatosDireccion validar(DatosDireccion d) {
        return new DatosDireccion(
                texto("alias", d.alias(), 40, true), texto("calle", d.calle(), 160, true),
                texto("comuna", d.comuna(), 80, true), texto("region", d.region(), 80, true),
                texto("referencia", d.referencia(), 200, false), d.principal());
    }

    private static DatosDireccion conPrincipal(DatosDireccion d, boolean principal) {
        return new DatosDireccion(d.alias(), d.calle(), d.comuna(), d.region(), d.referencia(), principal);
    }

    private static String texto(String campo, String valor, int max, boolean obligatorio) {
        String v = valor == null ? "" : valor.strip();
        if (v.isEmpty()) {
            if (obligatorio) {
                throw new ValidacionException(campo, "Es obligatorio.");
            }
            return null;
        }
        if (v.length() > max) {
            throw new ValidacionException(campo, "Máximo " + max + " caracteres.");
        }
        return v;
    }
}
