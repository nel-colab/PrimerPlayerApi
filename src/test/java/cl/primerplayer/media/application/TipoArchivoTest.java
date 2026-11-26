package cl.primerplayer.media.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import cl.primerplayer.media.application.ArchivosService.Tipo;

/**
 * Detección del tipo de imagen por sus primeros bytes ("magic bytes"), sin confiar en el Content-Type ni en la
 * extensión que manda el cliente. Cada caso inválido rompe una sola condición, para que un cambio en cualquiera de
 * ellas se note.
 */
class TipoArchivoTest {

    static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0);
    static final byte[] PNG = bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
    static final byte[] WEBP = bytes('R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P');

    @Test
    void detectaLosTresFormatosAdmitidos() {
        assertThat(Tipo.detectar(JPEG)).isEqualTo(Tipo.JPEG);
        assertThat(Tipo.detectar(PNG)).isEqualTo(Tipo.PNG);
        assertThat(Tipo.detectar(WEBP)).isEqualTo(Tipo.WEBP);
    }

    @Test
    void cadaTipoTraeSuMimeYExtension() {
        assertThat(Tipo.JPEG.mime).isEqualTo("image/jpeg");
        assertThat(Tipo.JPEG.extension).isEqualTo("jpg");
        assertThat(Tipo.PNG.mime).isEqualTo("image/png");
        assertThat(Tipo.WEBP.extension).isEqualTo("webp");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("noSonImagenesAdmitidas")
    void loQueNoEsJpegPngNiWebpSeRechaza(String caso, byte[] contenido) {
        assertThat(Tipo.detectar(contenido)).isNull();
    }

    static Stream<Arguments> noSonImagenesAdmitidas() {
        return Stream.of(
                Arguments.of("vacío", new byte[0]),
                // JPEG: FF D8 FF
                Arguments.of("JPEG truncado", bytes(0xFF, 0xD8)),
                Arguments.of("JPEG con 2.º byte distinto", con(JPEG, 1, 0x00)),
                Arguments.of("JPEG con 3.er byte distinto", con(JPEG, 2, 0x00)),
                // PNG: 89 'P' 'N' 'G' y al menos 8 bytes de firma
                Arguments.of("PNG truncado", bytes(0x89, 'P', 'N', 'G')),
                Arguments.of("PNG con 'P' distinta", con(PNG, 1, 'X')),
                Arguments.of("PNG con 'N' distinta", con(PNG, 2, 'X')),
                Arguments.of("PNG con 'G' distinta", con(PNG, 3, 'X')),
                // WEBP: 'RIFF' .... 'WEBP' (12 bytes)
                Arguments.of("WEBP truncado", bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B')),
                Arguments.of("RIFF con 'I' distinta", con(WEBP, 1, 'X')),
                Arguments.of("RIFF con 'F' distinta", con(WEBP, 2, 'X')),
                Arguments.of("RIFF con 2.ª 'F' distinta", con(WEBP, 3, 'X')),
                Arguments.of("RIFF que no es WEBP (W)", con(WEBP, 8, 'A')),   // por ejemplo un WAVE o un AVI
                Arguments.of("RIFF que no es WEBP (E)", con(WEBP, 9, 'X')),
                Arguments.of("RIFF que no es WEBP (B)", con(WEBP, 10, 'X')),
                Arguments.of("RIFF que no es WEBP (P)", con(WEBP, 11, 'X')),
                // Otros formatos comunes que alguien podría intentar subir
                Arguments.of("GIF", bytes('G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0, 0, 0)),
                Arguments.of("PDF", bytes('%', 'P', 'D', 'F', '-', '1', '.', '7', 0, 0, 0, 0)),
                Arguments.of("texto", "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes()));
    }

    private static byte[] bytes(int... valores) {
        byte[] b = new byte[valores.length];
        for (int i = 0; i < valores.length; i++) {
            b[i] = (byte) valores[i];
        }
        return b;
    }

    /** Copia de {@code base} con un byte cambiado. */
    private static byte[] con(byte[] base, int posicion, int valor) {
        byte[] b = base.clone();
        b[posicion] = (byte) valor;
        return b;
    }
}
