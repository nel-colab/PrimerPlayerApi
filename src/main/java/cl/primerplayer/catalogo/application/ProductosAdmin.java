package cl.primerplayer.catalogo.application;

import cl.primerplayer.catalogo.api.DatosProducto;
import cl.primerplayer.catalogo.api.ProductoAdmin;
import cl.primerplayer.catalogo.domain.Producto;

final class ProductosAdmin {

    private ProductosAdmin() {}

    static ProductoAdmin de(Producto p) {
        DatosProducto d = p.datos();
        return new ProductoAdmin(p.id(), d.nombre(), d.descripcion(), d.precio(), d.categoria(), d.tipo(), d.idioma(),
                d.marca(), d.plataforma(), d.franquicia(), d.linea(), d.codigoBarras(), d.limitePorCliente(),
                p.stock(), p.popularidad(), p.fechaIngreso(), d.publicado(), d.imagenes(), p.version(),
                p.actualizadoEn());
    }
}
