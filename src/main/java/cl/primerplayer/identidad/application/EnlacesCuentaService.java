package cl.primerplayer.identidad.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.primerplayer.identidad.api.EnlacesCuentaUseCase;
import cl.primerplayer.identidad.application.port.out.CuentaRepositorio;
import cl.primerplayer.identidad.application.port.out.CuentaRepositorio.FilaCuenta;
import cl.primerplayer.identidad.application.port.out.TokenUnUsoRepositorio;
import cl.primerplayer.identidad.domain.TokenOpaco;
import cl.primerplayer.identidad.domain.TokenUnUso;

@Service
class EnlacesCuentaService implements EnlacesCuentaUseCase {

    private final CuentaRepositorio cuentas;
    private final TokenUnUsoRepositorio tokens;
    private final Clock clock;
    private final Duration verificacionTtl;
    private final Duration recuperacionTtl;

    EnlacesCuentaService(CuentaRepositorio cuentas, TokenUnUsoRepositorio tokens, Clock clock, IdentidadProperties props,
                         @Value("${pp.auth.recuperacion-ttl:1h}") Duration recuperacionTtl) {
        this.cuentas = cuentas;
        this.tokens = tokens;
        this.clock = clock;
        this.verificacionTtl = props.verificacionTtl();
        this.recuperacionTtl = recuperacionTtl;
    }

    @Override
    @Transactional
    public Optional<TokenEmitido> emitirVerificacion(long usuarioId) {
        return cuentas.porId(usuarioId)
                .filter(FilaCuenta::activo)
                .filter(c -> c.emailVerificadoEn() == null)   // verificó mientras el correo esperaba: no se envía
                .map(c -> emitir(c.id(), TokenUnUso.Tipo.VERIFICACION, verificacionTtl));
    }

    @Override
    @Transactional
    public Optional<TokenEmitido> emitirRecuperacion(long usuarioId) {
        return cuentas.porId(usuarioId)
                .filter(FilaCuenta::activo)
                .map(c -> emitir(c.id(), TokenUnUso.Tipo.RECUPERACION, recuperacionTtl));
    }

    private TokenEmitido emitir(long usuarioId, TokenUnUso.Tipo tipo, Duration ttl) {
        Instant ahora = clock.instant();
        tokens.invalidarPendientes(usuarioId, tipo, ahora);   // solo sirve el enlace del último correo
        TokenOpaco token = TokenOpaco.generar();
        tokens.guardar(new TokenUnUso(java.util.UUID.randomUUID(), usuarioId, tipo, token.hash(), ahora.plus(ttl)));
        return new TokenEmitido(token.valor(), ttl);
    }
}
