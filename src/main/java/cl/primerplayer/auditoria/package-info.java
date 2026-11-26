/**
 * Módulo auditoría: registro de acciones sensibles ({@code auditoria}) y su consulta (solo ADMIN). Escribe escuchando
 * eventos de dominio publicados por los demás módulos; nadie lo llama directamente.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Auditoría",
    allowedDependencies = { "shared", "identidad :: api", "identidad :: events", "catalogo :: events" }
)
package cl.primerplayer.auditoria;
