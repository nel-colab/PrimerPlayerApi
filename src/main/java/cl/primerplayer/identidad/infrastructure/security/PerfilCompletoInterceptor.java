package cl.primerplayer.identidad.infrastructure.security;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import cl.primerplayer.identidad.api.CredencialesInvalidasException;
import cl.primerplayer.identidad.api.CorreoSinVerificarException;
import cl.primerplayer.identidad.api.CuentaUseCase;
import cl.primerplayer.identidad.api.CuentaUseCase.Perfil;
import cl.primerplayer.identidad.api.PerfilIncompletoException;
import cl.primerplayer.shared.api.RecursoNoEncontradoException;
import cl.primerplayer.shared.api.UsuarioActual;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * El personal (VENDEDOR / ADMIN) debe tener el correo verificado (403 {@code email-not-verified}; con el enlace o con
 * Google) y nombre, teléfono y RUT (403 {@code profile-incomplete}) para trabajar en {@code /admin/**}.
 * {@code /me/**} queda libre para que pueda resolverlo.
 * <p>Se consulta la base en cada request (no un claim del JWT) para que el desbloqueo sea inmediato al guardar el
 * perfil, sin esperar a que se renueve el token. Corre después de Spring Security: el rol ya fue verificado.
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
class PerfilCompletoInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private final CuentaUseCase cuenta;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/v1/admin/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioActual usuario)) {
            return true;   // sin sesión: Spring Security ya respondió 401/403 antes de llegar aquí
        }
        Perfil perfil;
        try {
            perfil = cuenta.perfil(usuario.id());
        } catch (RecursoNoEncontradoException e) {
            throw new CredencialesInvalidasException();   // desactivado o eliminado tras emitir el token
        }
        if (!perfil.emailVerificado()) {
            throw new CorreoSinVerificarException();
        }
        List<String> faltantes = perfil.camposFaltantes();
        if (!faltantes.isEmpty()) {
            throw new PerfilIncompletoException(faltantes);
        }
        return true;
    }
}
