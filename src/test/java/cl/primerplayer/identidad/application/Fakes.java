package cl.primerplayer.identidad.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import cl.primerplayer.identidad.api.EmailEnUsoException;
import cl.primerplayer.identidad.application.port.out.EmisorTokenAcceso;
import cl.primerplayer.identidad.application.port.out.HasherPassword;
import cl.primerplayer.identidad.application.port.out.RefreshTokenRepositorio;
import cl.primerplayer.identidad.application.port.out.TokenUnUsoRepositorio;
import cl.primerplayer.identidad.application.port.out.UsuarioRepositorio;
import cl.primerplayer.identidad.domain.RefreshToken;
import cl.primerplayer.identidad.domain.TokenUnUso;
import cl.primerplayer.identidad.domain.Usuario;

/** Adaptadores en memoria: la arquitectura hexagonal permite probar los casos de uso sin Spring ni base. */
final class Fakes {

    private Fakes() {}

    static final class UsuariosEnMemoria implements UsuarioRepositorio {
        final Map<Long, Usuario> porId = new HashMap<>();
        private long secuencia = 1;

        @Override
        public Optional<Usuario> buscarPorEmail(String email) {
            return porId.values().stream().filter(u -> u.email().equalsIgnoreCase(email)).findFirst();
        }

        @Override
        public Optional<Usuario> buscarPorId(long id) {
            return Optional.ofNullable(porId.get(id));
        }

        /** Usuarios baneados en las pruebas: id → baneo. */
        final Map<Long, Baneo> baneos = new HashMap<>();

        @Override
        public Optional<Baneo> baneoVigente(long usuarioId, java.time.Instant ahora) {
            return Optional.ofNullable(baneos.get(usuarioId))
                    .filter(b -> b.hasta() == null || b.hasta().isAfter(ahora));
        }

        @Override
        public Optional<Usuario> buscarPorGoogleSub(String googleSub) {
            return Optional.empty();
        }

        @Override
        public Optional<String> googleSubDe(long usuarioId) {
            return Optional.empty();
        }

        @Override
        public void vincularGoogle(long usuarioId, String googleSub, Instant ahora) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean enListaNegra(long usuarioId) {
            return false;
        }

        @Override
        public java.util.List<Usuario> buscarPorIds(java.util.Collection<Long> ids) {
            return ids.stream().map(porId::get).filter(java.util.Objects::nonNull).toList();
        }

        @Override
        public boolean existeEmail(String email) {
            return buscarPorEmail(email).isPresent();
        }

        @Override
        public Usuario guardarNuevo(Usuario u) {
            if (existeEmail(u.email())) throw new EmailEnUsoException();
            Usuario conId = copia(u, secuencia++, u.intentosFallidos(), u.bloqueadoHasta());
            porId.put(conId.id(), conId);
            return conId;
        }

        @Override
        public boolean registrarIntentoFallido(long id, int max, Instant hasta, Instant ahora) {
            Usuario u = porId.get(id);
            boolean bloquea = u.intentosFallidos() + 1 >= max;
            Usuario nuevo = copia(u, id, bloquea ? 0 : u.intentosFallidos() + 1, bloquea ? hasta : u.bloqueadoHasta());
            porId.put(id, nuevo);
            return nuevo.estaBloqueado(ahora);
        }

        @Override
        public void registrarInicioExitoso(long id, Instant ahora) {
            porId.put(id, copia(porId.get(id), id, 0, null));
        }

        private static Usuario copia(Usuario u, long id, int intentos, Instant bloqueadoHasta) {
            return Usuario.reconstituir(id, u.email(), u.nombre(), u.passwordHash(), u.rol(), u.activo(),
                    u.anonimo(), u.emailVerificadoEn(), intentos, bloqueadoHasta, u.creadoEn(), u.eliminadoEn());
        }
    }

    static final class RefreshEnMemoria implements RefreshTokenRepositorio {
        final List<RefreshToken> guardados = new ArrayList<>();
        final List<String> hashesRevocados = new ArrayList<>();
        @Override public void guardar(RefreshToken token) { guardados.add(token); }
        @Override public int revocarFamiliaDe(String tokenHash, Instant ahora) {
            hashesRevocados.add(tokenHash);
            return 1;
        }
        @Override public Optional<RefreshToken> buscarParaRotar(String tokenHash) {
            return guardados.stream().filter(t -> t.tokenHash().equals(tokenHash)).findFirst();
        }
        @Override public void marcarRotado(java.util.UUID id, java.util.UUID sucesor, Instant ahora) {
            reemplazar(id, t -> new RefreshToken(t.id(), t.usuarioId(), t.familia(), t.tokenHash(), t.expiraEn(),
                    t.creadoEn(), t.ip(), t.userAgent(), ahora, sucesor));
        }
        @Override public int revocarFamilia(java.util.UUID familia, Instant ahora) {
            int n = 0;
            for (RefreshToken t : List.copyOf(guardados)) {
                if (t.familia().equals(familia) && t.revocadoEn() == null) {
                    reemplazar(t.id(), x -> new RefreshToken(x.id(), x.usuarioId(), x.familia(), x.tokenHash(),
                            x.expiraEn(), x.creadoEn(), x.ip(), x.userAgent(), ahora, x.reemplazadoPor()));
                    n++;
                }
            }
            return n;
        }
        RefreshToken porId(java.util.UUID id) {
            return guardados.stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
        }
        private void reemplazar(java.util.UUID id, java.util.function.UnaryOperator<RefreshToken> f) {
            guardados.replaceAll(t -> t.id().equals(id) ? f.apply(t) : t);
        }
    }

    static final class TokensUnUsoEnMemoria implements TokenUnUsoRepositorio {
        final List<TokenUnUso> guardados = new ArrayList<>();
        @Override public void guardar(TokenUnUso token) { guardados.add(token); }
    }

    /** "Hash" reversible solo para pruebas. */
    static final class HasherFalso implements HasherPassword {
        int comparaciones;
        @Override public String hashear(String p) { return "hash:" + p; }
        @Override public boolean coincide(String p, String hash) { comparaciones++; return hash.equals("hash:" + p); }
        @Override public String hashSenuelo() { return "hash:senuelo"; }
    }

    static final class EmisorFalso implements EmisorTokenAcceso {
        @Override public TokenAcceso emitir(Usuario u, Instant ahora) { return new TokenAcceso("jwt-" + u.id(), 900); }
    }
}
