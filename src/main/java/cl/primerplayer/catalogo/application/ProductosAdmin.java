package cl.primerplayer.catalogo.application;

import java.util.List;
import java.util.Objects;

import cl.primerplayer.catalogo.api.DatosProducto;
import cl.primerplayer.catalogo.api.DatosProducto.ImagenProducto;
import cl.primerplayer.catalogo.api.ProductoAdmin;
import cl.primerplayer.catalogo.domain.Producto;

final class ProductosAdmin {

    private ProductosAdmin() {}

    static ProductoAdmin de(Producto p) {
        DatosProducto d = p.datos();
        return new ProductoAdmin(p.id(), d.nombre(), d.descripcion(), d.precio(), d.categoria(), d.tipo(), d.idioma(),
                d.marca(), d.plataforma(), d.franquicia(), d.linea(), d.codigoBarras(), d.limitePorCliente(),
                p.stock(), p.popularidad(), p.fechaIngreso(), d.publicado(), d.imagenes(), p.aprobacion(),
                p.cambioPendiente(), p.version(), p.actualizadoEn());
    }

    static DatosProducto conPublicado(DatosProducto d, boolean publicado) {
        return new DatosProducto(d.nombre(), d.descripcion(), d.precio(), d.categoria(), d.tipo(), d.idioma(),
                d.marca(), d.plataforma(), d.franquicia(), d.linea(), d.codigoBarras(), d.limitePorCliente(),
                publicado, d.imagenes());
    }

    /**
     * Mismos datos editables. Una imagen subida se compara por {@code archivoId}: al leerla trae su URL resuelta y en
     * el request puede venir sin ella.
     */
    static boolean mismosDatos(DatosProducto a, DatosProducto b) {
        return a.precio() == b.precio() && a.publicado() == b.publicado()
                && Objects.equals(a.nombre(), b.nombre()) && Objects.equals(a.descripcion(), b.descripcion())
                && Objects.equals(a.categoria(), b.categoria()) && Objects.equals(a.tipo(), b.tipo())
                && Objects.equals(a.idioma(), b.idioma()) && Objects.equals(a.marca(), b.marca())
                && Objects.equals(a.plataforma(), b.plataforma()) && Objects.equals(a.franquicia(), b.franquicia())
                && Objects.equals(a.linea(), b.linea()) && Objects.equals(a.codigoBarras(), b.codigoBarras())
                && Objects.equals(a.limitePorCliente(), b.limitePorCliente())
                && claves(a.imagenes()).equals(claves(b.imagenes()));
    }

    private static List<String> claves(List<ImagenProducto> imagenes) {
        return imagenes.stream()
                .map(i -> (i.archivoId() != null ? "archivo:" + i.archivoId() : "url:" + i.url()) + ":" + i.portada())
                .toList();
    }
}
