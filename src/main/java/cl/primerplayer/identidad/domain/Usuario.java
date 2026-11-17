package cl.primerplayer.identidad.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import cl.primerplayer.shared.api.Rol;

/** Agregado Usuario. Modelo puro: sin anotaciones de JPA ni de Spring. */
public final class Usuario {

    private final Long id;
    private final String email;
    private final String nombre;
    private final String passwordHash;
    private final Rol rol;
    private final boolean activo;
    private final boolean anonimo;
    private final Instant emailVerificadoEn;
    private final int intentosFallidos;
    private final Instant bloqueadoHasta;
    private final Instant creadoEn;
    private final Instant eliminadoEn;

    private Usuario(Long id, String email, String nombre, String passwordHash, Rol rol, boolean activo,
                    boolean anonimo, Instant emailVerificadoEn, int intentosFallidos, Instant bloqueadoHasta,
                    Instant creadoEn, Instant eliminadoEn) {
        this.id = id;
        this.email = Objects.requireNonNull(email);
        this.nombre = Objects.requireNonNull(nombre);
        this.passwordHash = passwordHash;
        this.rol = Objects.requireNonNull(rol);
        this.activo = activo;
        this.anonimo = anonimo;
        this.emailVerificadoEn = emailVerificadoEn;
        this.intentosFallidos = intentosFallidos;
        this.bloqueadoHasta = bloqueadoHasta;
        this.creadoEn = Objects.requireNonNull(creadoEn);
        this.eliminadoEn = eliminadoEn;
    }

    /** Registro público: siempre CLIENTE, activo y con el correo sin verificar. */
    public static Usuario registrarCliente(String nombre, String email, String passwordHash, Instant ahora) {
        return new Usuario(null, normalizarEmail(email), nombre.strip(), Objects.requireNonNull(passwordHash),
                Rol.CLIENTE, true, false, null, 0, null, ahora, null);
    }

    /** Usuario creado por la tienda (o la semilla de desarrollo): rol a elección y correo ya verificado. */
    public static Usuario crearVerificado(String nombre, String email, String passwordHash, Rol rol, Instant ahora) {
        return new Usuario(null, normalizarEmail(email), nombre.strip(), Objects.requireNonNull(passwordHash),
                rol, true, false, ahora, 0, null, ahora, null);
    }

    public static Usuario reconstituir(Long id, String email, String nombre, String passwordHash, Rol rol,
                                       boolean activo, boolean anonimo, Instant emailVerificadoEn,
                                       int intentosFallidos, Instant bloqueadoHasta, Instant creadoEn,
                                       Instant eliminadoEn) {
        return new Usuario(id, email, nombre, passwordHash, rol, activo, anonimo, emailVerificadoEn,
                intentosFallidos, bloqueadoHasta, creadoEn, eliminadoEn);
    }

    public static String normalizarEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    /** El cliente anónimo de mostrador, los inactivos y los eliminados nunca inician sesión. */
    public boolean puedeIniciarSesion() {
        return passwordHash != null && activo && !anonimo && eliminadoEn == null;
    }

    /** Para comprar en la web: cuenta activa, no eliminada, no anónima y con el correo verificado. */
    public boolean puedeComprar() {
        return activo && !anonimo && eliminadoEn == null && emailVerificadoEn != null;
    }

    public boolean estaBloqueado(Instant ahora) {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
    }

    public Long id() { return id; }
    public String email() { return email; }
    public String nombre() { return nombre; }
    public String passwordHash() { return passwordHash; }
    public Rol rol() { return rol; }
    public boolean activo() { return activo; }
    public boolean anonimo() { return anonimo; }
    public Instant emailVerificadoEn() { return emailVerificadoEn; }
    public int intentosFallidos() { return intentosFallidos; }
    public Instant bloqueadoHasta() { return bloqueadoHasta; }
    public Instant creadoEn() { return creadoEn; }
    public Instant eliminadoEn() { return eliminadoEn; }

    @Override
    public String toString() {   // sin hash de contraseña
        return "Usuario[id=" + id + ", email=" + email + ", rol=" + rol + "]";
    }
}
