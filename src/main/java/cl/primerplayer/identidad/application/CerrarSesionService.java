package cl.primerplayer.identidad.application;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.identidad.api.CerrarSesionUseCase;
import cl.primerplayer.identidad.application.port.out.RefreshTokenRepositorio;
import cl.primerplayer.identidad.domain.TokenOpaco;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class CerrarSesionService implements CerrarSesionUseCase {

    private final RefreshTokenRepositorio refreshTokens;
    private final Clock clock;

    @Override
    @Transactional
    public void cerrarSesion(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;   // sin cookie: no hay sesión que cerrar, igual se responde 204
        }
        refreshTokens.revocarFamiliaDe(TokenOpaco.hashDe(refreshToken), clock.instant());
    }
}
