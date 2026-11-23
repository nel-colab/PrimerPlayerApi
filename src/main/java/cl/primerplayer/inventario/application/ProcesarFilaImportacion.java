package cl.primerplayer.inventario.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.catalogo.api.AdministrarProductosUseCase;
import cl.primerplayer.catalogo.api.ConsultarProductosAdminUseCase;
import cl.primerplayer.catalogo.api.DatosProducto;
import cl.primerplayer.catalogo.api.ProductoAdmin;
import cl.primerplayer.inventario.api.AjustarStockUseCase;
import cl.primerplayer.inventario.api.AjustarStockUseCase.ReemplazoStock;
import cl.primerplayer.inventario.api.ImportarProductosUseCase.FilaImportacion;
import cl.primerplayer.shared.api.Rol;
import cl.primerplayer.shared.api.ValidacionException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class ProcesarFilaImportacion {

    private static final String MOTIVO = "Importación Excel";

    private final ConsultarProductosAdminUseCase consultar;
    private final AdministrarProductosUseCase administrar;
    private final AjustarStockUseCase stock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Resultado procesar(FilaImportacion fila, long usuarioId) {
        ProductoAdmin existente = buscarExistente(fila);
        DatosProducto datos = datos(fila, existente);
        ProductoAdmin guardado = existente == null
                ? administrar.crear(datos)
                : administrar.actualizar(existente.id(), existente.version(), datos, Rol.ADMIN);
        stock.reemplazar(new ReemplazoStock(guardado.id(), valor(fila.stock()), MOTIVO, usuarioId));
        return existente == null ? Resultado.CREADO : Resultado.ACTUALIZADO;
    }

    private ProductoAdmin buscarExistente(FilaImportacion fila) {
        String codigo = limpio(fila.codigoBarras());
        if (codigo != null) {
            var porCodigo = consultar.porCodigoBarrasActivo(codigo);
            if (porCodigo.isPresent()) {
                return porCodigo.get();
            }
        }
        return consultar.porNombreExacto(fila.nombre().strip()).orElse(null);
    }

    private static DatosProducto datos(FilaImportacion fila, ProductoAdmin existente) {
        String categoria = valor(fila.categoria(), existente == null ? null : existente.categoria());
        if (categoria == null) {
            throw new ValidacionException("categoria", "La categoría es requerida para crear un producto.");
        }
        return new DatosProducto(
                fila.nombre().strip(),
                valor(fila.descripcion(), existente == null ? null : existente.descripcion()),
                valor(fila.precio()),
                categoria,
                valor(fila.tipo(), existente == null ? null : existente.tipo()),
                valor(fila.idioma(), existente == null ? null : existente.idioma()),
                valor(fila.marca(), existente == null ? null : existente.marca()),
                valor(fila.plataforma(), existente == null ? null : existente.plataforma()),
                valor(fila.franquicia(), existente == null ? null : existente.franquicia()),
                valor(fila.linea(), existente == null ? null : existente.linea()),
                valor(fila.codigoBarras(), existente == null ? null : existente.codigoBarras()),
                valor(fila.limitePorUsuario(), existente == null ? null : existente.limitePorCliente()),
                Boolean.TRUE.equals(fila.publicado()),
                existente == null ? java.util.List.of() : existente.imagenes());
    }

    private static String valor(String nuevo, String anterior) {
        String limpio = limpio(nuevo);
        return limpio == null ? anterior : limpio;
    }

    private static String limpio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private static long valor(Long valor) {
        return valor == null ? 0 : valor;
    }

    private static int valor(Integer valor) {
        return valor == null ? 0 : valor;
    }

    private static Integer valor(Integer nuevo, Integer anterior) {
        return nuevo == null ? anterior : nuevo;
    }

    enum Resultado { CREADO, ACTUALIZADO }
}
