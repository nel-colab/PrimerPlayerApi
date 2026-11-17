package cl.primerplayer.shared.api;

/** Rol del usuario. {@link #frontend()} es el nombre que usa el frontend Angular. */
public enum Rol {
    CLIENTE("usuario"),
    VENDEDOR("vendedor"),
    ADMIN("admin");

    private final String frontend;

    Rol(String frontend) { this.frontend = frontend; }

    public String frontend() { return frontend; }

    /** Acepta el nombre del frontend ({@code usuario}, {@code vendedor}, {@code admin}) o el de la base. */
    public static Rol deFrontend(String valor) {
        for (Rol r : values()) {
            if (r.frontend.equalsIgnoreCase(valor) || r.name().equalsIgnoreCase(valor)) {
                return r;
            }
        }
        throw new ValidacionException("rol", "Use usuario, vendedor o admin.");
    }
}
