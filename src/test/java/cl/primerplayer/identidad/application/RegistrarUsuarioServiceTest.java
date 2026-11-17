package cl.primerplayer.identidad.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cl.primerplayer.identidad.api.EmailEnUsoException;
import cl.primerplayer.identidad.api.RegistrarUsuarioUseCase.RegistrarUsuarioCommand;
import cl.primerplayer.shared.api.Rol;
import cl.primerplayer.identidad.domain.TokenOpaco;
import cl.primerplayer.identidad.domain.TokenUnUso;
import cl.primerplayer.identidad.events.UsuarioRegistrado;
import cl.primerplayer.shared.api.ValidacionException;

class RegistrarUsuarioServiceTest {

    static final Instant AHORA = Instant.parse("2026-11-11T15:00:00Z");

    Fakes.UsuariosEnMemoria usuarios;
    Fakes.TokensUnUsoEnMemoria tokens;
    List<Object> eventos;
    RegistrarUsuarioService servicio;

    @BeforeEach
    void setUp() {
        usuarios = new Fakes.UsuariosEnMemoria();
        tokens = new Fakes.TokensUnUsoEnMemoria();
        eventos = new ArrayList<>();
        var props = new IdentidadProperties(Duration.ofDays(30), Duration.ofHours(24),
                new IdentidadProperties.Bloqueo(5, Duration.ofMinutes(15)), Duration.ofSeconds(10));
        servicio = new RegistrarUsuarioService(usuarios, tokens, new Fakes.HasherFalso(), eventos::add, props,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void registra_clienteConCorreoNormalizadoTokenDeVerificacionYEvento() {
        var r = servicio.registrar(new RegistrarUsuarioCommand(" Ash ", " Ash@PuebloPaleta.cl", "Pikachu2026!"));

        assertThat(r.email()).isEqualTo("ash@pueblopaleta.cl");
        assertThat(r.nombre()).isEqualTo("Ash");
        assertThat(r.rol()).isEqualTo(Rol.CLIENTE);
        assertThat(usuarios.porId.get(r.id()).passwordHash()).isEqualTo("hash:Pikachu2026!");
        assertThat(usuarios.porId.get(r.id()).emailVerificadoEn()).isNull();

        assertThat(tokens.guardados).singleElement().satisfies(t -> {
            assertThat(t.tipo()).isEqualTo(TokenUnUso.Tipo.VERIFICACION);
            assertThat(t.expiraEn()).isEqualTo(AHORA.plus(Duration.ofHours(24)));
        });
        assertThat(eventos).singleElement().isInstanceOfSatisfying(UsuarioRegistrado.class, e ->
                assertThat(TokenOpaco.hashDe(e.tokenVerificacion())).isEqualTo(tokens.guardados.getFirst().tokenHash()));
    }

    @Test
    void correoRepetido_sinImportarMayusculas_emailTaken() {
        servicio.registrar(new RegistrarUsuarioCommand("Ash", "ash@pueblopaleta.cl", "Pikachu2026!"));
        assertThatThrownBy(() -> servicio.registrar(
                new RegistrarUsuarioCommand("Otro", "ASH@pueblopaleta.cl", "Pikachu2026!")))
                .isInstanceOf(EmailEnUsoException.class)
                .extracting(e -> ((EmailEnUsoException) e).code()).isEqualTo("email-taken");
    }

    @Test
    void passwordDeMasDe72Bytes_validation() {
        String larga = "ñ".repeat(40);   // 40 caracteres, 80 bytes en UTF-8
        assertThatThrownBy(() -> servicio.registrar(new RegistrarUsuarioCommand("Ash", "a@b.cl", larga)))
                .isInstanceOf(ValidacionException.class);
        assertThat(usuarios.porId).isEmpty();
    }
}
