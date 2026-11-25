package cl.primerplayer.catalogo.domain;

/**
 * Validación GTIN: EAN-8, UPC-A (12) o EAN-13 con dígito verificador válido. Los productos sin código de fábrica
 * reciben un EAN-13 interno ({@link #eanInterno}): la tienda no escribe números inventados, que casi nunca tendrían
 * un verificador válido y podrían coincidir con el código de un producto real.
 */
public final class CodigoBarras {

    /** Prefijo GS1 200–299: "circulación restringida", reservado para uso interno de cada tienda. */
    static final String PREFIJO_INTERNO = "200";
    static final long MAX_CORRELATIVO = 999_999_999L;   // 9 dígitos entre el prefijo y el verificador

    private CodigoBarras() {}

    public static boolean esValido(String codigo) {
        if (codigo == null || !codigo.matches("\\d{8}|\\d{12}|\\d{13}")) {
            return false;
        }
        return verificador(codigo.substring(0, codigo.length() - 1)) == codigo.charAt(codigo.length() - 1) - '0';
    }

    /**
     * EAN-13 de uso interno: {@code 200} + correlativo de 9 dígitos + dígito verificador. Nunca coincide con el
     * código de un producto de fábrica (esos usan prefijos de país o empresa, no 200).
     */
    public static String eanInterno(long correlativo) {
        if (correlativo < 1 || correlativo > MAX_CORRELATIVO) {
            throw new IllegalArgumentException("Correlativo fuera de rango: " + correlativo);
        }
        String base = PREFIJO_INTERNO + String.format("%09d", correlativo);
        return base + verificador(base);
    }

    /** Dígito verificador GTIN de {@code base} (el código sin su último dígito). */
    private static int verificador(String base) {
        int suma = 0;
        // Desde el último dígito de la base hacia la izquierda, pesos 3, 1, 3, 1...
        for (int i = base.length() - 1, peso = 3; i >= 0; i--, peso = 4 - peso) {
            suma += (base.charAt(i) - '0') * peso;
        }
        return (10 - suma % 10) % 10;
    }
}
