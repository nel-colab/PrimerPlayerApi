package cl.primerplayer.identidad.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import cl.primerplayer.identidad.api.EmailEnUsoException;
import cl.primerplayer.identidad.application.port.out.UsuarioRepositorio;
import cl.primerplayer.identidad.domain.Usuario;
import lombok.RequiredArgsConstructor;

/** Adaptador de salida: implementa el puerto {@link UsuarioRepositorio} con Spring Data JPA. */
@Component
@RequiredArgsConstructor
class UsuarioPersistenceAdapter implements UsuarioRepositorio {

    private final UsuarioJpaRepository jpa;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Override
    public Optional<Baneo> baneoVigente(long usuarioId, Instant ahora) {
        return jdbc.query("""
                SELECT baneo_motivo, baneo_hasta FROM usuario
                WHERE id = ? AND baneo_motivo IS NOT NULL AND (baneo_hasta IS NULL OR baneo_hasta > ?)
                """, (rs, n) -> new Baneo(rs.getString("baneo_motivo"),
                        Optional.ofNullable(rs.getObject("baneo_hasta", java.time.OffsetDateTime.class))
                                .map(java.time.OffsetDateTime::toInstant).orElse(null)),
                usuarioId, ahora.atOffset(java.time.ZoneOffset.UTC)).stream().findFirst();
    }

    @Override
    public boolean enListaNegra(long usuarioId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT lista_negra_motivo IS NOT NULL FROM usuario WHERE id = ?", Boolean.class, usuarioId));
    }

    @Override
    public Optional<Usuario> buscarPorEmail(String email) {
        return jpa.buscarPorEmail(email).map(UsuarioPersistenceAdapter::aDominio);
    }

    @Override
    public Optional<Usuario> buscarPorId(long id) {
        return jpa.findById(id).map(UsuarioPersistenceAdapter::aDominio);
    }

    @Override
    public java.util.List<Usuario> buscarPorIds(java.util.Collection<Long> ids) {
        return jpa.findAllById(ids).stream().map(UsuarioPersistenceAdapter::aDominio).toList();
    }

    @Override
    public boolean existeEmail(String email) {
        return jpa.existeEmail(email);
    }

    @Override
    public Usuario guardarNuevo(Usuario u) {
        UsuarioEntity e = new UsuarioEntity();
        e.setEmail(u.email());
        e.setNombre(u.nombre());
        e.setPasswordHash(u.passwordHash());
        e.setRol(u.rol());
        e.setActivo(u.activo());
        e.setAnonimo(u.anonimo());
        e.setEmailVerificadoEn(u.emailVerificadoEn());
        e.setIntentosFallidos((short) u.intentosFallidos());
        e.setBloqueadoHasta(u.bloqueadoHasta());
        e.setCreadoEn(u.creadoEn());
        e.setActualizadoEn(u.creadoEn());
        e.setEliminadoEn(u.eliminadoEn());
        try {
            return aDominio(jpa.saveAndFlush(e));
        } catch (DataIntegrityViolationException ex) {
            // Carrera entre dos registros simultáneos con el mismo correo: gana uq_usuario_email.
            throw new EmailEnUsoException();
        }
    }

    @Override
    public boolean registrarIntentoFallido(long usuarioId, int maxIntentos, Instant bloquearHasta, Instant ahora) {
        return jpa.registrarIntentoFallido(usuarioId, maxIntentos, bloquearHasta, ahora);
    }

    @Override
    public void registrarInicioExitoso(long usuarioId, Instant ahora) {
        jpa.reiniciarIntentos(usuarioId, ahora);
        jdbc.update("UPDATE usuario SET ultimo_acceso_en = ? WHERE id = ?",
                ahora.atOffset(java.time.ZoneOffset.UTC), usuarioId);
    }

    private static Usuario aDominio(UsuarioEntity e) {
        return Usuario.reconstituir(e.getId(), e.getEmail(), e.getNombre(), e.getPasswordHash(), e.getRol(),
                e.isActivo(), e.isAnonimo(), e.getEmailVerificadoEn(), e.getIntentosFallidos(),
                e.getBloqueadoHasta(), e.getCreadoEn(), e.getEliminadoEn());
    }
}
