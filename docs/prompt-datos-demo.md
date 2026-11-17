# Tarea: poblar la base con datos de demostración completos (y dejarlos listos para retirarlos después)

Trabajas en `primer-player-api` (Spring Boot 4, PostgreSQL 18, Flyway), el backend de Primer Player: tienda de TCG,
figuras, videojuegos y eventos en San Bernardo, Chile. Lee `CLAUDE.md` y usa las skills `/primer-player-dominio` y
`/migracion-flyway` antes de escribir SQL.

El objetivo es **recorrer la aplicación completa con datos realistas**: cada pantalla, cada rol y cada estado de cada
flujo debe tener algo que mostrar. Más adelante, en una tarea aparte, **se retirarán todos los datos dummy**, así que
deben quedar aislados, inventariados y fáciles de borrar.

La tarea tiene dos fases. **Ejecuta solo la Fase 1.** La Fase 2 queda documentada para cuando se pida.

---

## Fuentes de datos

1. **Esquema (fuente de verdad):** `src/main/resources/db/migration/V1…V7`. Lee todas las migraciones completas,
   incluidos los `CHECK`, índices únicos parciales, la restricción `EXCLUDE` de cumpleaños y el trigger
   `tg_pagos_cuadran`.
2. **Datos base que ya existen en todos los ambientes (V6, no se tocan):** categorías, franquicias, el cliente anónimo
   `mostrador@primerplayer.cl` y `config_inventario` (umbral de stock bajo = 3).
3. **Datos de ejemplo del frontend.** El frontend vigente es `E:\proyectos\PrimerPlayerFront` (no uses
   `E:\proyectos\primerPlayer`, es una versión antigua). Úsalos como base para que el front y la API muestren lo mismo:
   - `src/app/core/data/mock-products.ts`: catálogo generado por código (nombres, categorías, franquicias, precios,
     stock, códigos de barras, imágenes, descripciones).
   - `src/app/core/data/mock-events.ts`: eventos (League Challenge, Prelanzamiento, League Cup…).
   - `src/app/core/data/mock-sales.ts`: ventas de mostrador generadas día a día con un PRNG determinista y una mezcla
     ponderada de medios de pago.
   - `src/app/core/data/mock-usuarios.ts`: usuarios del panel de administración.
   - Modelos en `src/app/core/models/` para entender cada campo.

   Lee estos archivos **ejecutando la lógica mentalmente o con un script de Node descartable** (en el scratchpad, no
   en el repo). Para los productos, lo que manda es el resultado generado, no solo el código fuente. Si un dato del
   front viola una regla del esquema (código de barras inválido, URL de imagen sin `https://`, etc.), corrígelo y anótalo.

---

## Fase 1 — Semilla de demostración

### Dónde y cómo

- Archivos en `src/main/resources/db/seed-dev/`. Esa ruta solo se carga con el perfil `dev`
  (`application-dev.yaml`), así que **nunca llega a producción ni a las pruebas**. Reemplaza el `R__datos_demo.sql`
  actual, que solo tiene un comentario.
- Usa **migraciones repetibles** (`R__`), divididas por área y con un nombre que fije el orden (Flyway ordena las
  repetibles por descripción):
  `R__demo_01_usuarios.sql`, `R__demo_02_catalogo.sql`, `R__demo_03_inventario.sql`, `R__demo_04_eventos.sql`,
  `R__demo_05_pedidos_y_pagos.sql`, `R__demo_06_caja.sql`, `R__demo_07_auditoria_y_correos.sql`.
- **Idempotentes:** una repetible se vuelve a ejecutar cada vez que cambia su contenido. Cada script debe poder
  correr dos veces sin duplicar ni fallar (`ON CONFLICT DO NOTHING`, `WHERE NOT EXISTS`, o borrar primero lo propio
  del área).
- **No cambies el esquema.** Nada de columnas nuevas ni migraciones `V__` para la demo.
- **No uses IDs fijos.** Las columnas son `GENERATED ALWAYS AS IDENTITY`. Relaciona por claves naturales: email,
  slug, código de barras, código de pedido, y título más fecha para eventos.

### Problemas conocidos que debes resolver

1. **Usuarios de desarrollo.** `admin@`, `vendedor@` y `cliente@primerplayer.cl` los crea
   `identidad/infrastructure/dev/UsuariosDemoDev.java` (un `ApplicationRunner`) **después** de Flyway, con un hash
   Argon2id. En una base nueva la semilla no puede referenciarlos. Solución recomendada: genera **una vez** el hash de
   `PrimerPlayer2026!` con el `HasherPassword` de la aplicación (por ejemplo con una prueba temporal que lo imprima y
   luego borras), e inserta esas tres cuentas y los clientes demo desde `R__demo_01_usuarios.sql` con ese hash.
   `UsuariosDemoDev` ya omite los emails existentes, así que no hay conflicto. Así **todos** los usuarios demo pueden
   iniciar sesión con la misma contraseña. Si eliges otra solución, justifícala.
2. **Secuencia de códigos de pedido.** Si insertas pedidos con código `PP-AAAA-NNNNNN`, al final ajusta
   `pedido_codigo_seq` con `setval` por encima del mayor número usado, para que la app no genere códigos duplicados.
3. **Trigger de pagos (diferido).** La suma de `pago.monto` de un pedido debe ser exactamente `pedido.total`. Inserta
   el pedido, sus ítems y **todos** sus pagos en el mismo script. Los pedidos sin pago (`PENDIENTE_PAGO`,
   `RECHAZADO`, `EXPIRADO`) no llevan filas en `pago`.
4. **Fechas que no envejecen.** Los eventos deben quedar en el futuro y el historial en el pasado cada vez que se
   recree la base. Calcula las fechas relativas al día de carga en `America/Santiago`, por ejemplo
   `date_trunc('day', now() AT TIME ZONE 'America/Santiago') + interval '…'` convertido de vuelta a `timestamptz`.
   No escribas fechas absolutas, salvo para casos que deban ser pasados.
5. **Determinismo.** Las ventas históricas deben salir iguales en cada carga. Reproduce la idea de `mock-sales.ts`
   con `generate_series` y una función determinista (por ejemplo `hashtext(dia || n)`), no con `random()`.

### Coherencia obligatoria (el dato debe poder haber sido producido por la aplicación)

- **Stock:** `producto.stock` = `stock_resultante` del último `movimiento_stock` del producto. Los movimientos forman
  una cadena coherente: un `INGRESO` inicial y luego ventas, ajustes y anulaciones con sus cantidades. Cada venta de
  producto tiene su movimiento `VENTA_WEB` o `VENTA_MOSTRADOR` con `referencia` = código del pedido.
- **Cupos:** `evento.cupos_ocupados` = suma de `cantidad` de sus inscripciones `RESERVADA` y `CONFIRMADA`. Cada
  inscripción apunta a un pedido con un ítem `event` del mismo evento, con snapshots iguales a los del evento.
- **Pedidos:** `canal = 'tienda'` ⇔ `vendedor_id` no nulo. Las ventas de mostrador anónimas usan
  `mostrador@primerplayer.cl`. `total` = suma de los subtotales de los ítems. `pagado_en` solo en estados pagados.
  `expira_en` solo en web `PENDIENTE_PAGO`. Respeta `limite_por_cliente` acumulado.
- **Pagos:** `efectivo` lleva `efectivo_recibido >= monto` y `vuelto_entregado` correcto. `debito`, `credito` y
  `prepago` llevan `numero_boleta`. `transferencia` lleva un `archivo` en el bucket `private-docs`. `cartas-tcg`
  lleva nombre, código y valor de la carta. `webpay` va con su `transaccion_webpay` y un `buy_order` de 26
  caracteres como máximo.
- **Webpay:** el estado de la transacción debe ser coherente con el del pedido (`AUTORIZADA` → pedido pagado;
  `RECHAZADA` o `ANULADA_USUARIO` → `RECHAZADO`; `EXPIRADA` → `EXPIRADO`; `REVERSADA`/`ANULADA` → `REEMBOLSADO`).
  Rellena `response_code`, `authorization_code`, `card_last4`, etc. con valores verosímiles de integración.
  **Nunca** números de tarjeta completos.
- **Caja:** un `cierre_caja` por día con ventas en tienda. Sus montos (`total_ventas`, `numero_ventas`, `subtotales`,
  `efectivo_neto`, `esperado_en_caja`, `diferencia`) **se calculan a partir de los pagos de ese día** en hora de
  Santiago, no se inventan. Incluye algún día con diferencia distinta de cero y uno con historial en
  `cierre_caja_version`. Deja **el día de hoy sin cerrar** para probar el cierre desde la app.
- **Cumpleaños:** `cupos_totales` nulo, `fecha_hora_fin` obligatoria y sin traslapes (la restricción `EXCLUDE` lo
  rechaza). Toda solicitud `APROBADA` tiene su evento; toda `RECHAZADA` tiene motivo.
- **Imágenes:** usa `url_externa` con `https://` (las del front si cumplen), una sola portada por producto y como
  máximo 5 imágenes. Las filas de `archivo` (comprobantes) son metadatos dummy. Indica en el inventario que el
  objeto no existe en MinIO.
- **Montos** enteros en CLP. **Fechas** `timestamptz`.

### Cobertura: cada estado debe aparecer al menos una vez

| Área | Casos que deben existir |
|---|---|
| Usuarios | 3 cuentas dev · ~15 clientes demo · un 2.º vendedor · uno sin verificar · uno bloqueado (`bloqueado_hasta` futuro) · uno inactivo · uno eliminado lógicamente · clientes con y sin RUT y teléfono · direcciones (una principal por usuario) |
| Catálogo | todos los productos del front · al menos uno en **cada** categoría hoja · publicados y no publicados · uno eliminado lógicamente · stock 0 · stock bajo (≤ 3) · con `limite_por_cliente` · sin código de barras · varios idiomas, marcas y plataformas |
| Inventario | movimientos de los 6 tipos (`INGRESO`, `VENTA_WEB`, `VENTA_MOSTRADOR`, `AJUSTE` positivo y negativo, `ANULACION`, `RESERVA_EXPIRADA`) · favoritos · alertas de stock pendientes y notificadas (solo de productos agotados) |
| Eventos | los 8 tipos · los eventos del front · uno lleno · uno casi lleno · uno con `limite_por_cliente` · uno no publicado · uno eliminado · uno pasado · cumpleaños aprobados que bloquean la agenda |
| Solicitudes de cumpleaños | `PENDIENTE` (varias, para la bandeja), `APROBADA`, `RECHAZADA` y `CANCELADA` |
| Pedidos | los 8 estados · canal web y tienda · pedidos mixtos (productos + eventos) · el cliente `cliente@primerplayer.cl` con historial variado · ventas de ~90 días hacia atrás para los reportes |
| Inscripciones | `RESERVADA`, `CONFIRMADA`, `CANCELADA` y `EXPIRADA` |
| Pagos | los 9 métodos · pagos mixtos (un pedido con 2 o más medios) · uno con `saldo_a_favor` |
| Webpay | los 8 estados de `transaccion_webpay` |
| Caja | historial de cierres · diferencias · una versión reemplazada · hoy abierto |
| Auditoría y correos | cambios de precio, de límite y de rol en `auditoria` · algunos `correo_enviado` (verificación, confirmación de pedido, alerta de stock) |

No siembres `refresh_token`, `token_un_uso`, `shedlock` ni la tabla de eventos de Spring Modulith.

### Marcar lo que es demo (para la Fase 2)

- Los clientes demo usan el dominio **`@demo.primerplayer.cl`**. Las 3 cuentas dev conservan `@primerplayer.cl`.
- Los códigos de barras inventados usan el prefijo GS1 de uso interno **`200`–`299`**.
- Todo lo demás (pedidos, pagos, eventos, caja) se identifica porque cuelga de usuarios o productos demo, o porque en
  dev **toda** la data de negocio es demo. La Fase 2 se apoya en eso.

### Entregables de la Fase 1

1. Los scripts `R__demo_*.sql` en `src/main/resources/db/seed-dev/`, comentados por bloque (qué caso cubre cada uno).
2. **`docs/datos-demo.md`**, el inventario y la guía de recorrido. Contiene:
   - Cuentas de acceso (email, rol, contraseña, estado) y para qué sirve cada una.
   - Una tabla por área con lo sembrado y **qué caso de prueba representa** cada dato llamativo (p. ej. "League Cup
     de diciembre: lleno, prueba el error `event-full`").
   - **Recorridos de navegación por rol**, paso a paso, con la pantalla o endpoint, el dato demo que se usa y el
     resultado esperado:
     - *Visitante:* catálogo → filtros por categoría y franquicia → búsqueda → detalle → agenda de eventos.
     - *Cliente:* login → favoritos y alertas → carrito → checkout → Webpay → "Mis pedidos" → "Mis inscripciones" →
       solicitar un cumpleaños → ver su estado.
     - *Vendedor:* mantenedor de productos (stock bajo y agotados) → ajuste de stock → eventos → bandeja de
       solicitudes de cumpleaños (aprobar y rechazar) → POS → cierre de caja de hoy.
     - *Admin:* todo lo anterior más reportes, usuarios (bloqueado, inactivo, cambio de rol) y auditoría.
   - En cada recorrido, marca qué parte **ya funciona** en la API (hoy: `identidad`, `catalogo`, `inventario` y
     `eventos`) y qué parte solo se ve en pgAdmin hasta que exista su módulo.
   - Consultas SQL de verificación para pgAdmin (`http://localhost:5050`), por ejemplo: stock = último movimiento,
     cupos = inscripciones, pagos = total, cierres = pagos del día, sin traslapes de cumpleaños. **Cada una debe
     devolver 0 filas** si los datos son coherentes.
   - Las correcciones hechas a datos del front que violaban el esquema.
3. Agrega en `README.md` un enlace a `docs/datos-demo.md` en la sección de usuarios de prueba.

### Verificación (muéstrame el resultado de cada paso)

1. Base desde cero, solo en local: `docker compose down -v && docker compose up -d`.
2. `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`: Flyway aplica V1–V7 y todas las `R__demo_*` sin errores,
   y la app arranca.
3. Reinicia la app sin borrar la base: no debe fallar ni duplicar nada (idempotencia).
4. Corre las consultas de verificación de `docs/datos-demo.md`: todas en 0 filas.
5. Prueba con curl los endpoints ya implementados con los datos demo: login de los 4 tipos de cuenta (incluido el
   bloqueado, que debe fallar), catálogo público con filtros, detalle de evento lleno, `/me/solicitudes-cumpleanos`
   del cliente, `/admin/productos/indicadores`, movimientos de un producto y agenda ocupada.
6. `./mvnw verify` sigue en verde (las pruebas no deben ver la semilla demo).

Al terminar, dame un resumen corto con: archivos creados, conteo de filas por tabla, decisiones tomadas y cualquier
regla de negocio que los datos del front contradecían.

---

## Fase 2 — Retirar los datos dummy (NO ejecutar ahora)

Documenta en `docs/datos-demo.md` una sección "Cómo retirar la demo" con estos pasos, sin ejecutarlos:

1. **Base de datos (dev):**
   - Opción rápida: borrar `src/main/resources/db/seed-dev/R__demo_*.sql` y recrear la base
     (`docker compose down -v && docker compose up -d`).
   - Opción sin recrear: un script `scripts/limpiar-datos-demo.sql` (**no** es una migración) que borre toda la data
     de negocio en orden de llaves foráneas, deje intacto lo sembrado por V6 (categorías, franquicias,
     `mostrador@primerplayer.cl`, `config_inventario`), conserve opcionalmente las 3 cuentas dev, reinicie
     `pedido_codigo_seq` y elimine de `flyway_schema_history` las filas de las `R__demo_*`. Escríbelo ahora, pero no lo
     ejecutes.
   - Si `seed-dev` queda vacía, quitarla de `spring.flyway.locations` en `application-dev.yaml`.
2. **Frontend (`E:\proyectos\PrimerPlayerFront`):** para cada `// TODO(api)` y cada archivo de
   `src/app/core/data/mock-*.ts`, reemplazar el dummy por la llamada real **solo cuando el módulo del backend exista**,
   y borrar el mock cuando ya nadie lo importe. Lista ahora, en `docs/datos-demo.md`, qué servicios del front dependen
   de qué mock y qué módulo del backend falta para retirarlo.
3. **Comprobación final:** `grep` sin resultados de `mock-`, `TODO(api)`, `@demo.primerplayer.cl` y `R__demo_` en
   ambos repos, y la app funcionando de punta a punta sin datos dummy.
