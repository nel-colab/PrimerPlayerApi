package cl.primerplayer.catalogo.domain;

/**
 * Validación GTIN: EAN-8, UPC-A (12) o EAN-13 con dígito verificador válido.
 *
 * <p>TODO(pregunta-abierta): si la tienda usa códigos internos, relajar a 8–14 dígitos sin verificador
 * (también hay que cambiar el CHECK de {@code producto.codigo_barras}).
 */
public final class CodigoBarras {

    private CodigoBarras() {}

    public static boolean esValido(String codigo) {
        if (codigo == null || !codigo.matches("\\d{8}|\\d{12}|\\d{13}")) {
            return false;
        }
        int suma = 0;
        // Desde el dígito anterior al verificador hacia la izquierda, pesos 3, 1, 3, 1...
        for (int i = codigo.length() - 2, peso = 3; i >= 0; i--, peso = 4 - peso) {
            suma += (codigo.charAt(i) - '0') * peso;
        }
        int verificador = (10 - suma % 10) % 10;
        return verificador == codigo.charAt(codigo.length() - 1) - '0';
    }
}
