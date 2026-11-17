/**
 * Módulo identidad: registro, inicio de sesión, tokens (JWT de acceso + refresh opaco) y usuarios.
 *
 * <p>Arquitectura hexagonal:
 * <ul>
 *   <li>{@code api} — puertos de entrada (casos de uso), comandos, resultados y errores. Público.</li>
 *   <li>{@code events} — eventos de dominio publicados. Público.</li>
 *   <li>{@code domain} — modelo puro (sin Spring ni JPA).</li>
 *   <li>{@code application} — servicios que implementan los casos de uso; {@code application.port.out} define
 *       los puertos de salida.</li>
 *   <li>{@code infrastructure} — adaptadores de salida: persistencia JPA, BCrypt, JWT y Spring Security.</li>
 *   <li>{@code web} — adaptador de entrada REST.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Identidad",
    allowedDependencies = { "shared" }
)
package cl.primerplayer.identidad;
