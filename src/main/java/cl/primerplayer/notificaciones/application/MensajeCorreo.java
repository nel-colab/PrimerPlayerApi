package cl.primerplayer.notificaciones.application;

import java.util.List;
import java.util.Objects;

/**
 * Contenido de un correo, independiente del formato: la plantilla HTML con la marca (logo incrustado, centrado) y la
 * versión de texto plano salen de aquí. Regla de la tienda: <b>ningún correo lleva URLs, enlaces ni botones</b>; lo
 * que la persona debe hacer se explica con texto (y, si hace falta, con un código para escribir en la web).
 *
 * @param asunto       asunto (sin el sufijo de la marca: lo agrega {@link #asuntoCompleto()})
 * @param preencabezado texto corto que el cliente de correo muestra junto al asunto en la bandeja
 * @param titulo       título grande centrado
 * @param saludo       "Hola Ana:" (o {@code null})
 * @param parrafos     cuerpo, en orden
 * @param destacado    bloque central llamativo (un código, un total, un precio), o {@code null}
 * @param detalles     filas etiqueta/valor (resumen de un pedido, de un producto), puede ser vacía
 * @param pasos        instrucciones numeradas (cómo usar el código), puede ser vacía
 * @param nota         texto pequeño al final (seguridad, "si no fuiste tú"), o {@code null}
 */
public record MensajeCorreo(
        String asunto,
        String preencabezado,
        String titulo,
        String saludo,
        List<String> parrafos,
        Destacado destacado,
        List<Detalle> detalles,
        List<String> pasos,
        String nota) {

    public static final String MARCA = "Primer Player";

    public MensajeCorreo {
        Objects.requireNonNull(asunto);
        Objects.requireNonNull(titulo);
        parrafos = parrafos == null ? List.of() : List.copyOf(parrafos);
        detalles = detalles == null ? List.of() : List.copyOf(detalles);
        pasos = pasos == null ? List.of() : List.copyOf(pasos);
    }

    /**
     * @param etiqueta  texto pequeño sobre el valor ("Tu código", "Total pagado")
     * @param valor     el dato grande ("482913", "$12.990")
     * @param esCodigo  se muestra con letras separadas y fuente monoespaciada (el texto sigue siendo "482913": se
     *                  copia sin espacios)
     * @param pie       texto pequeño bajo el valor ("Vence en 24 horas"), o {@code null}
     */
    public record Destacado(String etiqueta, String valor, boolean esCodigo, String pie) {}

    public record Detalle(String etiqueta, String valor) {}

    public String asuntoCompleto() {
        return asunto + " · " + MARCA;
    }

    /** Versión de texto plano (clientes sin HTML y filtros de spam): mismo contenido, sin URLs. */
    public String textoPlano() {
        StringBuilder t = new StringBuilder();
        t.append(titulo.toUpperCase(java.util.Locale.ROOT)).append("\n\n");
        if (saludo != null) {
            t.append(saludo).append("\n\n");
        }
        parrafos.forEach(p -> t.append(p).append("\n\n"));
        if (destacado != null) {
            t.append(destacado.etiqueta()).append(": ").append(destacado.valor()).append('\n');
            if (destacado.pie() != null) {
                t.append(destacado.pie()).append('\n');
            }
            t.append('\n');
        }
        detalles.forEach(d -> t.append(d.etiqueta()).append(": ").append(d.valor()).append('\n'));
        if (!detalles.isEmpty()) {
            t.append('\n');
        }
        for (int i = 0; i < pasos.size(); i++) {
            t.append(i + 1).append(". ").append(pasos.get(i)).append('\n');
        }
        if (!pasos.isEmpty()) {
            t.append('\n');
        }
        if (nota != null) {
            t.append(nota).append("\n\n");
        }
        t.append("— ").append(MARCA).append(", San Bernardo\n");
        t.append("Este es un correo automático: no es necesario responderlo.\n");
        return t.toString();
    }
}
