package cl.primerplayer.identidad.infrastructure.security;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import cl.primerplayer.shared.web.Titulos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Seguridad HTTP: API sin estado con JWT Bearer. Las reglas por ruta (regla 9 de CLAUDE.md) se complementan con
 * {@code @PreAuthorize} en los métodos sensibles.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        http
            // Sin sesión ni cookies de autenticación: el JWT va en Authorization. El único endpoint con cookie
            // (/auth/refresh, pendiente) se protegerá con SameSite=Strict + X-Requested-With: pp-web.
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/**", "/api/v1/carrito/validacion").permitAll()
                // return_url de Webpay: lo llama el navegador del cliente que vuelve de Transbank (sin Bearer)
                .requestMatchers("/api/v1/pagos/webpay/retorno").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/categorias", "/api/v1/franquicias",
                                 "/api/v1/productos", "/api/v1/productos/*", "/api/v1/productos/*/relacionados",
                                 "/api/v1/eventos", "/api/v1/eventos/*", "/api/v1/agenda/ocupados").permitAll()
                .requestMatchers("/api/v1/openapi.json", "/api/v1/openapi.json/**",
                                 "/swagger-ui.html", "/swagger-ui/**", "/actuator/health").permitAll()
                .requestMatchers("/api/v1/admin/reportes/**", "/api/v1/admin/usuarios", "/api/v1/admin/usuarios/**",
                                 "/api/v1/admin/auditoria", "/api/v1/admin/auditoria/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/admin/**").hasAnyRole("VENDEDOR", "ADMIN")
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(rs -> rs
                .jwt(jwt -> jwt.jwtAuthenticationConverter(new UsuarioActualConverter()))
                .authenticationEntryPoint((req, res, ex) -> problema(req, res, HttpStatus.UNAUTHORIZED,
                        "unauthorized", "Se requiere un token de acceso válido."))
                .accessDeniedHandler((req, res, ex) -> problema(req, res, HttpStatus.FORBIDDEN,
                        "forbidden", "No tienes permiso para esta operación.")))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> problema(req, res, HttpStatus.UNAUTHORIZED,
                        "unauthorized", "Se requiere un token de acceso válido."))
                .accessDeniedHandler((req, res, e) -> problema(req, res, HttpStatus.FORBIDDEN,
                        "forbidden", "No tienes permiso para esta operación.")));
        return http.build();
    }

    /** Escribe problem+json a mano: estos errores ocurren en filtros, antes de llegar al @RestControllerAdvice. */
    private static void problema(HttpServletRequest req, HttpServletResponse res, HttpStatus status, String code,
                                 String detalle) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        if (status == HttpStatus.UNAUTHORIZED) {
            res.setHeader("WWW-Authenticate", "Bearer");
        }
        res.getWriter().write("""
            {"type":"https://api.primerplayer.cl/problemas/%s","title":"%s","status":%d,"code":"%s",\
            "detail":"%s","instance":"%s"}""".formatted(code, Titulos.de(code), status.value(), code, detalle,
                req.getRequestURI().replace("\\", "\\\\").replace("\"", "\\\"")));
    }
}
