package cl.primerplayer.checkout.web;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import cl.primerplayer.checkout.api.CheckoutUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * {@code return_url} de Webpay: Transbank devuelve aquí el navegador del cliente (GET o POST de formulario). Nunca
 * responde JSON: siempre redirige (303) a la página de resultado del frontend.
 */
@RestController
@RequiredArgsConstructor
@EnableConfigurationProperties(WebpayRetornoController.PaginaResultado.class)
@Tag(name = "Carrito y checkout")
class WebpayRetornoController {

    private final CheckoutUseCase checkout;
    private final PaginaResultado pagina;

    @RequestMapping(value = "/api/v1/pagos/webpay/retorno", method = {RequestMethod.GET, RequestMethod.POST})
    @Operation(summary = "Retorno desde Webpay (lo llama el navegador del cliente, no el frontend)",
            description = "Redirige 303 a pp.checkout.url-resultado?pedido=PP-... (o ?error=pago si no se reconoce).")
    ResponseEntity<Void> retorno(@RequestParam(name = "token_ws", required = false) String tokenWs,
                                 @RequestParam(name = "TBK_TOKEN", required = false) String tbkToken,
                                 @RequestParam(name = "TBK_ORDEN_COMPRA", required = false) String tbkOrdenCompra) {
        String codigo = checkout.procesarRetorno(vacioANull(tokenWs), vacioANull(tbkToken), vacioANull(tbkOrdenCompra));
        UriComponentsBuilder destino = UriComponentsBuilder.fromUriString(pagina.urlResultado());
        URI uri = (codigo == null ? destino.queryParam("error", "pago") : destino.queryParam("pedido", codigo))
                .build().encode().toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, uri.toString()).build();
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    @ConfigurationProperties(prefix = "pp.checkout")
    record PaginaResultado(String urlResultado) {}
}
