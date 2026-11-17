---
name: nuevo-modulo-spring
description: Crea o completa un módulo de negocio del monolito Spring Boot de Primer Player (identidad, catalogo, inventario, media, eventos, ventas, checkout, pagos, pos, caja, reportes, clientes, notificaciones, auditoria) con la estructura de paquetes, entidades, servicios, controladores, DTO, errores y pruebas estándar. Úsala al implementar un módulo nuevo, un caso de uso o un endpoint.
argument-hint: "[modulo] [caso de uso opcional]"
---

# Crear o completar un módulo

Argumentos: `$ARGUMENTS` (nombre del módulo y, opcionalmente, el caso de uso o endpoint).

## Antes de escribir código

1. Carga la skill `primer-player-dominio` y lee las reglas y endpoints de **este** módulo.
2. Lee las migraciones en `src/main/resources/db/migration/` de las tablas que el módulo posee.
   Si falta una tabla o columna, usa la skill `migracion-flyway` primero.
3. Revisa qué expone ya el paquete `api` de los módulos de los que dependes. Dependencias permitidas:

| Módulo | Puede depender de |
| --- | --- |
| `identidad` | `shared` |
| `media` | `shared` |
| `catalogo` | `media` |
| `inventario` | `catalogo` |
| `eventos` | `media` |
| `clientes` | `identidad`, `catalogo` |
| `ventas` | `catalogo`, `inventario`, `eventos`, `identidad` |
| `pagos` | `media` |
| `checkout` | `ventas`, `pagos` |
| `pos` | `ventas`, `pagos`, `inventario` |
| `caja`, `reportes` | `ventas`, `pagos` (solo lectura) |
| `notificaciones`, `auditoria` | nadie los usa; solo escuchan eventos |

Si necesitas una dependencia que no está en la tabla, **detente y pregunta**: probablemente es un evento.

## Estructura (ejemplo para `inventario`)

```text
cl/primerplayer/inventario/
├── package-info.java          # @ApplicationModule(allowedDependencies = {"catalogo"})
├── api/                       # público: interfaces + records + excepciones de negocio
├── events/                    # público: records de eventos de dominio
├── application/               # servicios @Service @Transactional (implementan api/)
├── domain/                    # entidades JPA y enums (package-private si se puede)
├── infrastructure/            # repositorios Spring Data, adaptadores externos
└── web/                       # @RestController + dto/ (records request/response) + mapper MapStruct
```

Plantillas listas para copiar en [plantillas/](plantillas/): `package-info.java.txt`, `Controller.java.txt`,
`Service.java.txt`, `ExcepcionNegocio.java.txt`, `IntegracionTest.java.txt`.

## Reglas al implementar

- Stock y cupos: solo vía `InventarioOperaciones` / `CuposOperaciones` (UPDATE condicional). Nunca `setStock()`.
- Métodos de `api` que modifican stock o cupos llevan `@Transactional(propagation = MANDATORY)`:
  el que orquesta (checkout, pos) abre la transacción.
- Nada de llamadas HTTP dentro de `@Transactional`. Webpay, S3 y correo van antes o después del commit.
- Publica eventos con `ApplicationEventPublisher`; escúchalos con `@ApplicationModuleListener` (outbox persistido).
- Errores de negocio: extiende `ExcepcionNegocio` con su `code`; el `@RestControllerAdvice` de `shared`
  los convierte en `problem+json`. No construyas respuestas de error a mano en los controladores.
- Fechas con el `Clock` inyectado. Montos `long`.
- Controladores delgados: validan (`@Valid`), llaman al servicio, mapean con MapStruct. Seguridad con
  `@PreAuthorize` en los métodos sensibles además de las reglas por ruta.
- Endpoints `/me/**`: el id del cliente sale de `@AuthenticationPrincipal`, nunca del request.

## Pruebas obligatorias por caso de uso

1. Unitaria del servicio con las reglas (casos felices y cada `code` de error).
2. Integración con Testcontainers (PostgreSQL real) que verifique las restricciones de la base.
3. Seguridad: cada endpoint probado con anónimo, CLIENTE, VENDEDOR y ADMIN.
4. Si toca stock, cupos o límites: prueba de concurrencia (N hilos, `CountDownLatch`) que demuestre que no hay sobreventa.
5. `./mvnw verify` debe pasar (incluye `ModularityTest`).

## Al terminar

Resume qué creaste, qué endpoints quedaron, qué pruebas corren y cualquier `TODO(pregunta-abierta)`.
