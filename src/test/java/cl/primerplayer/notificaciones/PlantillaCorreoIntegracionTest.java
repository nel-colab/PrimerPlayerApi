package cl.primerplayer.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import cl.primerplayer.TestcontainersConfiguration;
import cl.primerplayer.notificaciones.application.MensajeCorreo;
import cl.primerplayer.notificaciones.application.MensajeCorreo.Destacado;
import cl.primerplayer.notificaciones.application.MensajeCorreo.Detalle;
import cl.primerplayer.notificaciones.infrastructure.RenderizadorCorreo;

/** La plantilla única de los correos: marca incrustada, sin enlaces ni URLs, y el texto siempre escapado. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, CorreosCapturados.Config.class})
@ActiveProfiles("test")
class PlantillaCorreoIntegracionTest {

    @Autowired RenderizadorCorreo renderizador;

    static MensajeCorreo mensaje(String producto) {
        return new MensajeCorreo("Asunto de prueba", "Preencabezado", "Título grande", "¡Hola, Ana!",
                List.of("Primer párrafo.", "Segundo párrafo con " + producto),
                new Destacado("Tu código", "482913", true, "Vence en 24 horas"),
                List.of(new Detalle("Producto", producto), new Detalle("Total", "$12.990")),
                List.of("Paso uno.", "Paso dos."),
                "Nota final.");
    }

    @Test
    void html_conLogosIncrustadosYTodoElContenido_sinEnlacesNiUrls() {
        String html = renderizador.html(mensaje("Sobre Black Bolt"));

        assertThat(html)
                .contains("src=\"cid:logo-pp\"").contains("src=\"cid:logo-letras\"")
                .contains("Título grande").contains("¡Hola, Ana!").contains("Segundo párrafo con Sobre Black Bolt")
                .contains("482913").contains("Vence en 24 horas").contains("$12.990")
                .contains("Paso dos.").contains("Nota final.").contains("Preencabezado");
        assertThat(html.toLowerCase())
                .doesNotContain("<a ").doesNotContain("href").doesNotContain("http://").doesNotContain("https://")
                .doesNotContain(" th:");   // ningún atributo de Thymeleaf sin procesar
    }

    @Test
    void html_escapaElTextoQueEscribenLosVendedores() {
        String html = renderizador.html(mensaje("<a href=\"https://malo.test\">Gratis</a>"));
        assertThat(html).doesNotContain("<a href").contains("&lt;a href=");
    }

    @Test
    void textoPlano_mismoContenido_sinUrls() {
        String texto = mensaje("Sobre Black Bolt").textoPlano();
        assertThat(texto)
                .startsWith("TÍTULO GRANDE")
                .contains("Tu código: 482913").contains("Producto: Sobre Black Bolt").contains("1. Paso uno.")
                .doesNotContain("http");
    }
}
