package cl.primerplayer.catalogo.application;

import java.text.Normalizer;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.catalogo.api.AdministrarMaestrosUseCase;
import cl.primerplayer.catalogo.api.CategoriaDuplicadaException;
import cl.primerplayer.catalogo.api.CategoriaEnUsoException;
import cl.primerplayer.catalogo.api.FranquiciaDuplicadaException;
import cl.primerplayer.catalogo.api.FranquiciaEnUsoException;
import cl.primerplayer.catalogo.application.port.out.MaestrosAdminRepositorio;
import cl.primerplayer.catalogo.application.port.out.MaestrosAdminRepositorio.FilaCategoria;
import cl.primerplayer.catalogo.application.port.out.MaestrosAdminRepositorio.FilaFranquicia;
import cl.primerplayer.catalogo.events.MaestroCatalogoCambiado;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import cl.primerplayer.shared.api.ValidacionException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mantenedor de categorías y franquicias. Regla central: lo que alguna vez se asoció a un producto (aunque esté
 * eliminado, o solo en una revisión) no se elimina; la FK de {@code producto} es la red final.
 */
@Service
class MaestrosAdminService implements AdministrarMaestrosUseCase {

    static final int MAX_TITULO = 80;
    static final int MAX_SLUG = 60;
    static final int MAX_FRANQUICIA = 60;
    static final int MAX_ORDEN = 9999;
    static final String AGRUPADORA = "todos";

    private final MaestrosAdminRepositorio repo;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final JsonMapper json;

    MaestrosAdminService(MaestrosAdminRepositorio repo, ApplicationEventPublisher eventos, Clock clock, JsonMapper json) {
        this.repo = repo;
        this.eventos = eventos;
        this.clock = clock;
        this.json = json;
    }

    // ---------- categorías ----------

    @Override
    @Transactional(readOnly = true)
    public List<CategoriaAdmin> categorias() {
        return repo.categorias().stream().map(MaestrosAdminService::aCategoria).toList();
    }

    @Override
    @Transactional
    public CategoriaAdmin crearCategoria(long actorId, String titulo, String slug, Integer orden) {
        String t = titulo(titulo);
        String s = slug == null || slug.isBlank() ? slugDe(t) : slug.strip().toLowerCase(Locale.ROOT);
        validarSlug(s);
        int o = orden == null ? repo.siguienteOrden() : orden(orden);
        if (repo.existeCategoria(s, t, null)) {
            throw new CategoriaDuplicadaException();
        }
        int id;
        try {
            id = repo.crearCategoria(s, t, o);
        } catch (DuplicateKeyException e) {   // otra solicitud creó el mismo slug al mismo tiempo
            throw new CategoriaDuplicadaException();
        }
        FilaCategoria creada = categoria(id);
        auditar(actorId, "categoria", id, "CATEGORIA_CREADA", null, datos(creada));
        return aCategoria(creada);
    }

    @Override
    @Transactional
    public CategoriaAdmin actualizarCategoria(long actorId, int id, String titulo, Integer orden) {
        FilaCategoria antes = categoria(id);
        String t = titulo(titulo);
        int o = orden == null ? antes.orden() : orden(orden);
        if (repo.existeCategoria(antes.slug(), t, id)) {
            throw new CategoriaDuplicadaException();
        }
        repo.actualizarCategoria(id, t, o);
        FilaCategoria despues = categoria(id);
        auditar(actorId, "categoria", id, "CATEGORIA_EDITADA", datos(antes), datos(despues));
        return aCategoria(despues);
    }

    @Override
    @Transactional
    public void eliminarCategoria(long actorId, int id) {
        FilaCategoria c = categoria(id);
        if (c.enUso()) {
            throw new CategoriaEnUsoException();
        }
        try {
            repo.eliminarCategoria(id);
        } catch (DataIntegrityViolationException e) {   // un producto la tomó entre la revisión y el borrado
            throw new CategoriaEnUsoException();
        }
        auditar(actorId, "categoria", id, "CATEGORIA_ELIMINADA", datos(c), null);
    }

    // ---------- franquicias ----------

    @Override
    @Transactional(readOnly = true)
    public List<FranquiciaAdmin> franquicias() {
        return repo.franquicias().stream().map(MaestrosAdminService::aFranquicia).toList();
    }

    @Override
    @Transactional
    public FranquiciaAdmin crearFranquicia(long actorId, String nombre) {
        String n = nombreFranquicia(nombre);
        if (repo.existeFranquicia(n, null)) {
            throw new FranquiciaDuplicadaException();
        }
        int id;
        try {
            id = repo.crearFranquicia(n);
        } catch (DuplicateKeyException e) {
            throw new FranquiciaDuplicadaException();
        }
        FilaFranquicia creada = franquicia(id);
        auditar(actorId, "franquicia", id, "FRANQUICIA_CREADA", null, Map.of("nombre", n));
        return aFranquicia(creada);
    }

    @Override
    @Transactional
    public FranquiciaAdmin renombrarFranquicia(long actorId, int id, String nombre) {
        FilaFranquicia antes = franquicia(id);
        String n = nombreFranquicia(nombre);
        if (n.equals(antes.nombre())) {
            return aFranquicia(antes);
        }
        if (repo.existeFranquicia(n, id)) {
            throw new FranquiciaDuplicadaException();
        }
        try {
            repo.renombrarFranquicia(id, n);
        } catch (DuplicateKeyException e) {
            throw new FranquiciaDuplicadaException();
        }
        repo.renombrarFranquiciaEnRevisionesPendientes(antes.nombre(), n);
        auditar(actorId, "franquicia", id, "FRANQUICIA_EDITADA", Map.of("nombre", antes.nombre()), Map.of("nombre", n));
        return aFranquicia(franquicia(id));
    }

    @Override
    @Transactional
    public void eliminarFranquicia(long actorId, int id) {
        FilaFranquicia f = franquicia(id);
        if (f.enUso()) {
            throw new FranquiciaEnUsoException();
        }
        try {
            repo.eliminarFranquicia(id);
        } catch (DataIntegrityViolationException e) {
            throw new FranquiciaEnUsoException();
        }
        auditar(actorId, "franquicia", id, "FRANQUICIA_ELIMINADA", Map.of("nombre", f.nombre()), null);
    }

    // ---------- reglas ----------

    /** "Juegos de Mesa & Más" → {@code juegos-de-mesa-mas}: sin acentos, minúsculas, guiones. Sin regex (lineal). */
    static String slugDe(String texto) {
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).toLowerCase(Locale.ROOT);
        StringBuilder b = new StringBuilder();
        boolean guionPendiente = false;
        for (int i = 0; i < sinAcentos.length() && b.length() < MAX_SLUG; i++) {
            char c = sinAcentos.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK) {
                continue;   // tilde o diéresis separada por la normalización NFD
            }
            if (esAlfanumerico(c)) {
                if (guionPendiente && !b.isEmpty() && b.length() < MAX_SLUG - 1) {
                    b.append('-');
                }
                guionPendiente = false;
                b.append(c);
            } else {
                guionPendiente = true;
            }
        }
        return b.toString();
    }

    /** Minúsculas, números y guiones simples entre ellos ({@code a-b-c}); sin guion al inicio ni al final. */
    static boolean esSlugValido(String slug) {
        if (slug.isEmpty() || slug.length() > MAX_SLUG || slug.charAt(0) == '-' || slug.charAt(slug.length() - 1) == '-') {
            return false;
        }
        for (int i = 0; i < slug.length(); i++) {
            char c = slug.charAt(i);
            boolean guionDoble = c == '-' && slug.charAt(i - 1) == '-';
            if (!(esAlfanumerico(c) || c == '-') || guionDoble) {
                return false;
            }
        }
        return true;
    }

    private static boolean esAlfanumerico(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    private static void validarSlug(String slug) {
        if (!esSlugValido(slug)) {
            throw new ValidacionException("slug",
                    "El identificador debe tener letras minúsculas, números y guiones (máx. " + MAX_SLUG + ").");
        }
        if (AGRUPADORA.equals(slug)) {
            throw new ValidacionException("slug", "\"todos\" está reservado para el catálogo completo.");
        }
    }

    private static String titulo(String titulo) {
        String t = titulo == null ? "" : titulo.strip().replaceAll("\\s+", " ");
        if (t.isEmpty() || t.length() > MAX_TITULO) {
            throw new ValidacionException("titulo", "El nombre es obligatorio (máx. " + MAX_TITULO + " caracteres).");
        }
        return t;
    }

    private static int orden(int orden) {
        if (orden < 0 || orden > MAX_ORDEN) {
            throw new ValidacionException("orden", "El orden debe estar entre 0 y " + MAX_ORDEN + ".");
        }
        return orden;
    }

    private static String nombreFranquicia(String nombre) {
        String n = nombre == null ? "" : nombre.strip().replaceAll("\\s+", " ");
        if (n.isEmpty() || n.length() > MAX_FRANQUICIA) {
            throw new ValidacionException("nombre", "El nombre es obligatorio (máx. " + MAX_FRANQUICIA + " caracteres).");
        }
        return n;
    }

    private FilaCategoria categoria(int id) {
        return repo.categoria(id).orElseThrow(() -> new RecursoNoEncontradoException("La categoría no existe."));
    }

    private FilaFranquicia franquicia(int id) {
        return repo.franquicia(id).orElseThrow(() -> new RecursoNoEncontradoException("La franquicia no existe."));
    }

    private static Map<String, Object> datos(FilaCategoria c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("slug", c.slug());
        m.put("titulo", c.titulo());
        m.put("orden", c.orden());
        return m;
    }

    private void auditar(long actorId, String entidad, int id, String accion, Map<String, ?> antes,
                         Map<String, ?> despues) {
        eventos.publishEvent(new MaestroCatalogoCambiado(actorId, entidad, id, accion,
                antes == null ? null : json.writeValueAsString(antes),
                despues == null ? null : json.writeValueAsString(despues), clock.instant()));
    }

    private static CategoriaAdmin aCategoria(FilaCategoria c) {
        return new CategoriaAdmin(c.id(), c.slug(), c.titulo(), c.orden(), c.productos(), c.enUso());
    }

    private static FranquiciaAdmin aFranquicia(FilaFranquicia f) {
        return new FranquiciaAdmin(f.id(), f.nombre(), f.productos(), f.enUso());
    }
}
