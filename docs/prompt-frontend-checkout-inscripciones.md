# Tarea: conectar al backend el carrito, el checkout con Webpay y las inscripciones

Trabajas en **PrimerPlayerFront** (`E:\proyectos\PrimerPlayerFront`), el frontend Angular 22 de Primer Player, una
tienda de TCG, figuras, videojuegos y eventos en San Bernardo, Chile. El backend es **primer-player-api**
(`E:\proyectos\primer-player-api`, Spring Boot 4 + PostgreSQL).

Este documento tiene tres partes:

1. **Qué ya está conectado a la API.** No lo rehagas; úsalo como modelo.
2. **Qué falta conectar y el backend ya tiene listo.** Es tu tarea.
3. **Qué NO toca en este prompt.** Algunas áreas el backend todavía no las tiene; otras sí las tiene, pero se
   conectarán en el próximo prompt. No las toques; solo verifica que estén marcadas.

Lee el documento completo antes de escribir código.

---

## 0. Reglas generales (no negociables)

- **Sigue los patrones que ya existen** en `src/app/core/services/`. Los modelos a imitar son `products-api.ts`,
  `pedidos-api.ts`, `events-api.ts`, `cumpleanos-api.ts` y `admin-stock-api.ts`:
  - el decorador `@Service()` de Angular 22,
  - `inject(HttpClient)`, `rxResource` o `httpResource`,
  - el token `API_BASE` (`/api/v1`) y el tipo `Pagina<T>` de `src/app/core/config/api.ts`,
  - signals (`signal`, `computed`, `linkedSignal`, `toSignal`).
- **Errores**: la API responde `application/problem+json` con `code`, `detail` y, a veces, `errors[]`. Usa los helpers
  de `src/app/core/http/api-problem.ts` (`toApiProblem`, `mensajeProblema`, `aplicarErroresCampo`). Agrega ahí los
  códigos nuevos, con su prueba en `api-problem.spec.ts`. No inventes otro manejo de errores.
- **Autenticación**: no la toques. `auth-interceptor.ts` agrega el `Bearer` y renueva la sesión ante un 401.
- **Rutas relativas** con `API_BASE`, nunca `http://localhost:8080`. En desarrollo, `proxy.conf.json` envía `/api` al
  backend.
- **SSR**: las rutas que dependen de la API o de la sesión se renderizan en el navegador (`RenderMode.Client` en
  `app.routes.server.ts`). Haz lo mismo con cada página nueva. `server.ts` no reenvía `/api` en producción.
- **Montos** en enteros CLP. **Fechas** en ISO 8601 con el desfase de Chile (`2026-11-14T15:00:00-03:00`).
- **Marca con `// TODO(api): pendiente en backend — <qué falta>`** lo que quede local, y **elimina el `TODO`** de lo
  que conectes.
- **No cambies el comportamiento visible** salvo donde este documento lo pida.
- **No cargues ni borres datos.** No ejecutes `reiniciar-datos.sh` ni SQL. La base está vacía a propósito, así que los
  listados responden vacíos. La lógica con datos se prueba en `ng test` con `HttpTestingController`.
- Antes de un cambio grande, **haz un inventario y muéstramelo** (sección 4.0).

### Antes de empezar

1. Comprueba que la API corriendo sea la versión actual. El código del backend tiene **51 endpoints**. Si
   `http://localhost:8080/api/v1/openapi.json` no trae `/me/pedidos`, `/checkout`, `/me/inscripciones` y
   `/admin/pos/ventas`, la API se levantó antes de compilar esos módulos. Pídeme reiniciarla
   (`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`); no mates procesos por tu cuenta.
2. **Si algo de este documento no coincide con el OpenAPI, manda el OpenAPI**: avísame la diferencia.
3. **Pruebas en Windows**: ejecuta `ng test` desde `E:\proyectos\PrimerPlayerFront`, con la unidad en mayúscula. Desde
   `e:\…`, Vitest carga dos copias de sí mismo y todas las suites fallan con «Vitest failed to find the runner».

---

## 1. Ya conectado a la API (no lo rehagas)

| Área | Servicio del front | Endpoint | Pantallas |
|---|---|---|---|
| Login, registro y sesión | `auth.ts`, `auth-interceptor.ts` | `/auth/login`, `/auth/registro`, `/auth/refresh`, `/auth/logout` | `/cuenta/login`, `/cuenta/registro` |
| Catálogo público | `products-api.ts` (con caché por id y `asegurar(ids)`) | `/productos`, `/productos/destacados`, `/productos/{id}`, `/productos/{id}/relacionados` | Inicio, `/catalogo/:categoria`, `/producto/:id` |
| Categorías y franquicias | `catalogo-api.ts` | `/categorias`, `/franquicias` | Navbar, mantenedor de stock |
| Mis pedidos | `pedidos-api.ts` | `/me/pedidos`, `/me/pedidos/{codigo}` | `/cuenta/perfil` (sección «Mis pedidos») |
| Panel de pedidos y retiro en tienda | `pedidos-api.ts` | `/admin/pedidos`, `/admin/pedidos/{codigo}/estado` | `/admin/pedidos` |
| Agenda de eventos (pública) | `events-api.ts` (con caché por id y `asegurar(ids)`) | `/eventos`, `/eventos/{id}`, `/agenda/ocupados` | `/eventos`, `/cuenta/cumpleanos` |
| Mantenedor de eventos | `admin-events-api.ts` | `/admin/eventos/**` | `/admin/eventos` |
| Solicitudes de cumpleaños | `cumpleanos-api.ts` | `/me/solicitudes-cumpleanos/**`, `/admin/solicitudes-cumpleanos/**` | `/cuenta/cumpleanos`, `/admin/cumpleanos` |
| Mantenedor de stock | `admin-stock-api.ts` | `/admin/productos/**` | `/admin/stock` |
| Inventario (ajustes, movimientos, umbral) | `admin-stock-api.ts` | `/admin/inventario/**` | `/admin/stock`, `/admin/inventario/configuracion` |

Los modelos `Pedido`, `ESTADOS_PEDIDO` y `esRetirable` están en `core/models/pedido.ts`. La etiqueta de estado con
color es el componente compartido `shared/estado-pedido`.

> Nota: desde ahora la respuesta del pedido (`/me/pedidos`, `/admin/pedidos`) trae además un campo `payments`. En
> este prompt **no lo uses**: se integra en el próximo, junto con el POS (ver sección 3). Que llegue no rompe nada.

---

## 2. Falta conectar y el backend YA lo tiene (tu tarea)

Estas áreas funcionan hoy con datos del navegador (`localStorage`). El backend ya tiene sus endpoints, probados
(Webpay contra el ambiente de integración real de Transbank).

| Área | Hoy en el front | Endpoint disponible |
|---|---|---|
| Validación del carrito | `cart.ts` valida en el navegador | `POST /carrito/validacion` |
| Checkout (pedido con reserva de stock y cupos por 15 min) | `cart.checkout()` crea un pedido simulado en `purchase-history.ts` | `POST /checkout` |
| Pago con Webpay Plus | No existe; el checkout confirma sin cobrar | Respuesta de `/checkout` + retorno `/pagos/webpay/retorno` |
| Página de resultado del pago | No existe | El backend redirige a `/pago/resultado?pedido=PP-…` o `?error=pago` |
| Compra de cupos de eventos | `event-reservations.ts` + carrito local | Va por el mismo `/checkout` (líneas `type: "event"`) |
| Mis inscripciones y su cancelación | `perfil.ts` usa `event-reservations.ts` | `GET /me/inscripciones`, `POST /me/inscripciones/{id}/cancelacion` |
| Inscritos de un evento (tienda) | No existe la pantalla | `GET /admin/eventos/{id}/inscritos` |

### 2.1 `POST /api/v1/carrito/validacion` (público)

Revisa el carrito con precios y disponibilidad actuales. **No reserva nada.** Con sesión de cliente también evalúa su
límite de compra. Siempre responde **200**: los problemas vienen por línea en `errors[]`.

Cuerpo (el mismo de `/checkout`). Los ítems repetidos van agrupados en una línea:

```json
{ "lineas": [ { "type": "product", "itemId": 42, "quantity": 2 }, { "type": "event", "itemId": 7, "quantity": 1 } ] }
```

Respuesta:

```json
{
  "lines": [
    { "type": "product", "itemId": 42, "name": "Sobre Pokémon Black Bolt", "quantity": 5,
      "unitPrice": 4990, "subtotal": 24950, "available": 3, "maxAllowed": 8 },
    { "type": "product", "itemId": 51, "name": "Elite Trainer Box", "quantity": 3,
      "unitPrice": 59990, "subtotal": 179970, "available": 10, "maxAllowed": 2 }
  ],
  "total": 204920,
  "valid": false,
  "errors": [
    { "line": 0, "itemType": "product", "itemId": 42, "code": "stock-limit", "maxAllowed": null },
    { "line": 1, "itemType": "product", "itemId": 51, "code": "purchase-limit", "maxAllowed": 2 }
  ]
}
```

- `lines[].available`: stock o cupos disponibles; `null` si el ítem no existe. **Es el número para «Solo quedan N».**
- `lines[].maxAllowed`: unidades que el cliente aún puede comprar por su límite; `null` = sin límite o sin sesión.
- `errors[].line` es el índice de la línea enviada. **`errors[].maxAllowed` solo viene en `purchase-limit`**; en el
  resto es `null`.
- Códigos posibles: `item-unavailable`, `out-of-stock`, `stock-limit`, `event-full`, `event-past`, `purchase-limit`,
  `invalid-quantity`.

### 2.2 `POST /api/v1/checkout` (solo CLIENTE)

- Requiere sesión **de cliente**. Un vendedor o admin con sesión recibe **403 `forbidden`**: el carrito debe explicar
  que las compras web son para cuentas de cliente.
- Requiere **correo verificado**; si no lo está, responde 403 `email-not-verified`.
- Requiere el encabezado **`Idempotency-Key`** con un **UUID nuevo por intento de compra**. Reenviar la misma clave con
  el mismo carrito devuelve el mismo pedido; con otro carrito responde 409 `idempotency-conflict`. Genera la clave al
  pulsar «Pagar» y reutilízala solo en los reintentos de esa misma compra (por ejemplo, un error de red).
- Reserva stock y cupos por **15 minutos**.
- Responde **201** con `Location: /api/v1/me/pedidos/{codigo}`:

```json
{
  "order":   { "code": "PP-2026-000123", "status": "PENDIENTE_PAGO", "total": 16980,
               "expiresAt": "2026-11-13T15:45:00-03:00" },
  "payment": { "provider": "webpay", "url": "https://webpay3gint.transbank.cl/webpayserver/initTransaction",
               "token": "01ab…" }
}
```

- **Si `payment` viene**, el navegador debe hacer un **POST de formulario** a `payment.url` con el campo
  `token_ws = payment.token`. Crea un `<form method="post">` con un `<input type="hidden" name="token_ws">` y envíalo;
  no uses `HttpClient`, porque es una navegación hacia Transbank.
- **Si `payment` es `null`**, navega directo a `/pago/resultado?pedido=<order.code>` y deja que esa página muestre el
  estado real. Pasa en dos casos:
  - el pedido quedó pagado de inmediato (total 0, eventos gratuitos);
  - se reintentó con la misma clave un pedido que ya se resolvió (por ejemplo, rechazado).
- Errores:
  - los mismos `errors[]` por línea de 2.1 (409; 422 para `invalid-quantity`);
  - `empty` (422): carrito vacío;
  - `validation` (400): falta `Idempotency-Key`, no es un UUID o hay ítems repetidos sin agrupar;
  - `forbidden` (403, no es cliente) y `email-not-verified` (403);
  - `idempotency-conflict` (409);
  - `payment-provider-error` (502): Webpay no respondió. El pedido se anula y el stock vuelve: el cliente puede
    reintentar con una **clave nueva**.
- En el 409 por stock no viene `lines`. Para mostrar cuánto queda, **revalida el carrito** con 2.1.

### 2.3 Retorno de Webpay y página `/pago/resultado` (página nueva)

Transbank devuelve el navegador del cliente a `/api/v1/pagos/webpay/retorno`. **El frontend nunca llama ese
endpoint**: el backend confirma o anula el pago y redirige (303) a la URL configurada en `pp.checkout.url-resultado`
(por defecto `http://localhost:4200/pago/resultado`):

- `/pago/resultado?pedido=PP-2026-000123`: pago procesado (aprobado o rechazado; el estado real lo da el pedido);
- `/pago/resultado?error=pago`: el retorno no se pudo asociar a un pedido.

Crea la página standalone `features/public/pago-resultado/` en la ruta `pago/resultado`, con `authGuard` y
`RenderMode.Client`. Debe:

- con `?pedido=`, cargar `GET /me/pedidos/{codigo}` (`PedidosApi.miPedido`) y mostrar el resultado según `status`:
  - `PAGADO` o `LISTO_RETIRO`: compra exitosa, ítems, total y aviso de retiro en tienda (`DIRECCION_RETIRO`);
  - `RECHAZADO`: pago rechazado, con un enlace para volver al carrito;
  - `PENDIENTE_PAGO`: pago en proceso; ofrece recargar;
  - `EXPIRADO` o `ANULADO`: la reserva venció o se anuló;
  - 404 `item-unavailable`: «El pedido no existe.»;
- con `?error=pago`, mostrar que no se pudo confirmar el pago y que no se realizó ningún cobro asociado;
- **vaciar el carrito local solo cuando el pedido quedó pagado**, no antes de ir a Webpay: si el pago se rechaza, el
  cliente debe poder reintentar con el mismo carrito.

### 2.4 Cambios en el carrito (`core/services/cart.ts` y `features/public/carrito/`)

- **Validación**: reemplaza la validación local de stock, cupos y límites por `POST /carrito/validacion`. Llámala al
  abrir `/carrito` y cada vez que cambie una cantidad (con un `debounceTime` corto, cancelando la anterior). Usa
  `lines[]` para precios y subtotales actuales, y `errors[]` para marcar la línea afectada con su mensaje.
- **Checkout**: reemplaza el checkout simulado por `POST /checkout` (2.2) y la redirección a Webpay.
- **Textos de demostración**: quita «Compra de demostración» y «Checkout de demostración: confirma el pedido sin
  procesar pagos reales» de `carrito.html`; el pago ahora es real (en el ambiente de integración).
- **Sin sesión**: el carrito se puede revisar; al pagar, lleva al login con `returnUrl=/carrito`, como hoy.
- **Correo sin verificar**: ante `email-not-verified`, explica que debe verificar su correo antes de comprar.
- **Sesión de vendedor o admin**: ante `forbidden`, explica que las compras web son para cuentas de cliente.
- **`purchase-history.ts`**: el checkout ya no debe escribir pedidos ahí. Si después de este cambio solo lo usa el POS,
  déjalo con su `TODO(api)` apuntando al POS. No lo borres: el POS sigue local en este prompt.

### 2.5 Compra de cupos de eventos (`features/public/eventos/eventos.ts`)

La compra de cupos pasa a ser una línea `type: "event"` del mismo carrito y del mismo `/checkout`. Quita la reserva
local (`event-reservations.ts`) del flujo de compra web y los `TODO(api)` de las líneas ~106 y ~129. Los cupos
disponibles salen de la API (`/eventos`) y, al validar el carrito, de `available`.

### 2.6 Mis inscripciones (`GET /api/v1/me/inscripciones`, solo CLIENTE)

Arreglo (no página), con las próximas primero y luego las pasadas:

```json
[{
  "id": 15, "eventId": 7, "eventType": "league-cup", "eventTitle": "League Cup Pokémon TCG",
  "eventDate": "2026-11-20T15:00:00-03:00", "eventPlace": "Primer Player",
  "unitPrice": 7000, "quantity": 1, "total": 7000,
  "status": "CONFIRMADA", "orderCode": "PP-2026-000123",
  "reservedAt": "2026-11-12T19:30:00-03:00", "cancelledAt": null, "cancellable": true
}]
```

- `status`: `RESERVADA` (esperando pago), `CONFIRMADA`, `CANCELADA` o `EXPIRADA`. Agrega etiquetas en un solo lugar,
  igual que `ESTADOS_PEDIDO`.
- Título, fecha, lugar y precio son los del momento de la compra.
- Muestra «Cancelar inscripción» **solo si `cancellable` es `true`**.

`POST /api/v1/me/inscripciones/{id}/cancelacion` responde 200 con la inscripción actualizada. Libera los cupos, pero
**no reembolsa** el pago (lo gestiona la tienda): dilo en la confirmación antes de cancelar. Errores:
404 `item-unavailable` (no existe o es ajena), 409 `registration-not-cancellable` (pendiente de pago, ya cancelada o
expirada) y 409 `event-past` (el evento ya comenzó).

En `perfil.ts`, la sección «Mis inscripciones» pasa a usar este endpoint y su cancelación. Quita de ahí
`event-reservations.ts`, `purchase-history.ts` y el `TODO(api)` de inscripciones. Como `/me/pedidos`, solo se llama
con rol cliente.

**Datos de la cuenta en el perfil (teléfono, RUT, «miembro desde")**: no los toques. `GET /me` hoy solo responde
`{email, name, role}`; esos datos llegarán con el módulo de cuenta del cliente. Deja los «—» con su `TODO(api)`.

### 2.7 Inscritos de un evento (`GET /api/v1/admin/eventos/{id}/inscritos`, VENDEDOR o ADMIN)

```json
{
  "eventId": 7, "confirmedSeats": 18, "reservedSeats": 2,
  "registrations": [
    { "id": 15, "customerName": "Cliente Demo", "email": "cliente@primerplayer.cl", "quantity": 1,
      "status": "CONFIRMADA", "orderCode": "PP-2026-000123", "channel": "web",
      "reservedAt": "2026-11-12T19:30:00-03:00", "cancelledAt": null }
  ]
}
```

Vigentes primero (por orden de inscripción), cancelados y expirados al final. Las ventas anónimas del POS aparecen
como `customerName: "Venta en mostrador"`. 404 `item-unavailable` si el evento no existe o fue eliminado.

Agrega una acción **«Ver inscritos»** en cada fila de `/admin/eventos`, que abra una vista (ruta hija
`/admin/eventos/:id/inscritos` o modal; elige lo que encaje con el mantenedor y explícalo) con cupos confirmados y
reservados, y la tabla de inscritos.

### 2.8 Errores nuevos para `api-problem.ts`

| `code` | Mensaje sugerido |
|---|---|
| `empty` | Tu carrito está vacío. |
| `event-full` | Ya no quedan cupos suficientes para este evento. |
| `event-past` | El evento ya comenzó o terminó. |
| `purchase-limit` | Superas el máximo que puedes comprar de este ítem. |
| `invalid-quantity` | La cantidad no es válida. |
| `email-not-verified` | Debes verificar tu correo antes de comprar. Revisa tu bandeja de entrada. |
| `idempotency-conflict` | Esta compra ya se inició con otro carrito. Recarga la página e inténtalo nuevamente. |
| `payment-provider-error` | No pudimos conectar con Webpay. Inténtalo nuevamente en unos minutos. |
| `registration-not-cancellable` | Esta inscripción ya no se puede cancelar. |

`out-of-stock`, `stock-limit` e `item-unavailable` ya existen. Hoy `out-of-stock` y `stock-limit` dicen «El ajuste
dejaría el stock en negativo» porque nacieron en el inventario. En el carrito usa un mensaje propio con el override de
`mensajeProblema`, por ejemplo «Sin stock» y «Solo quedan N unidades». Toma N de **`lines[i].available`** de la
validación, no de `errors[].maxAllowed`, que en estos códigos viene `null`. Para `purchase-limit` sí usa
`errors[].maxAllowed` («Puedes comprar N más»).

---

## 3. Lo que NO toca en este prompt

Deben quedar como están (locales o vacías), cada una con su `// TODO(api): …`. Si para cumplir una tarea de la
sección 2 crees que necesitas algo de esta lista, **detente y pregúntame**.

### 3.1 El backend YA lo tiene, pero se conecta en el próximo prompt

No lo hagas ahora. Cambia el texto del `TODO` a `// TODO(api): listo en backend — se conecta en el prompt del POS`.

| Área | Hoy en el front | Ya disponible en el backend |
|---|---|---|
| POS: lectura por código de barras (la pistola es un teclado: escribe el código y presiona Enter) | `/admin/codigo-barras`, `product-store.ts` | `GET /admin/pos/productos/codigo/{codigo}` |
| POS: venta en mostrador con pago mixto | `pos-sale.ts`, `event-store.ts`, `purchase-history.ts` | `POST /admin/pos/validacion`, `POST /admin/pos/ventas` |
| POS: anulación de una venta (solo ADMIN) | No existe | `POST /admin/pos/ventas/{codigo}/anulacion` |
| Pagos dentro del pedido | No se usa | Campo `payments` en la respuesta de los pedidos |

### 3.2 El backend AÚN NO lo tiene

| Área | Hoy en el front | Qué falta en el backend | Datos que ya están en la base sin endpoint |
|---|---|---|---|
| Cierre de caja | `cash-closures.ts`, `/admin/cierre-caja` | Módulo caja | `cierre_caja`, `cierre_caja_version` |
| Ventas, reportes y estadísticas | `sales-orders.ts`, `/admin/ventas` | Módulo reportes | `pedido`, `pedido_item`, `pago` |
| Administración de usuarios | `admin-usuarios-api.ts` (en memoria, parte vacío) | Módulo usuarios (contrato propuesto en `docs/api-admin-usuarios.md`) | `usuario`, `refresh_token` |
| Datos de la cuenta, edición del perfil y cambio de contraseña | «—» y botones deshabilitados en `/cuenta/perfil` | Módulo cuenta del cliente (`GET /me` hoy solo trae `email`, `name`, `role`) | `usuario` |
| Direcciones | Lista vacía en el perfil | Módulo cuenta del cliente (solo hay retiro en tienda) | `direccion` |
| Favoritos y alertas de stock | Listas vacías en el perfil | Módulo cuenta del cliente | `favorito`, `alerta_stock` |
| Subida de imágenes y comprobantes | Solo URLs `https://` en `stock-form` y `eventos-form` | Módulo media (MinIO) | `archivo`, `producto_imagen` |
| Correos enviados y auditoría | No existe pantalla | Sin endpoint | `correo_enviado`, `auditoria` |
| Despacho a domicilio | No existe | ⏸️ Decisión de negocio pendiente: solo retiro en tienda | — |
| Reembolsos | No existe | ⏸️ Decisión de negocio pendiente | — |

Orden previsto en el backend: caja → media → reportes → cuenta del cliente → usuarios.

---

## 4. Pasos

### 4.0 Inventario (muéstramelo antes de cambiar nada grande)

- quién usa `cart.checkout`, `PurchaseHistory`, `EventReservations` y `EventStore` después de estos cambios;
- qué páginas cambian (carrito, eventos, perfil, `/admin/eventos`) y cuáles quedan igual (POS, caja, ventas, usuarios);
- cualquier diferencia entre este documento y el OpenAPI.

### 4.1 Orden sugerido

1. Errores nuevos en `api-problem.ts` (2.8), con sus pruebas.
2. `core/services/checkout-api.ts` con `validar(lineas)` e `iniciar(lineas, idempotencyKey)`, y el envío del formulario
   a Webpay en una función aparte, fácil de probar.
3. Carrito (2.4) y compra de cupos (2.5).
4. Página `/pago/resultado` (2.3).
5. `core/services/inscripciones-api.ts`, con `mias()`, `cancelar(id)` e `inscritos(eventoId)`; luego el perfil (2.6) y
   la vista de inscritos (2.7).
6. Actualiza la tabla «Qué está conectado y qué sigue local» del `README.md` y, en el backend,
   `docs/estado-integracion.md` (cambia ⚠️ por ✅ en lo que conectes).

---

## 5. Verificación (muéstrame el resultado de cada punto)

Con el backend actualizado y **la base vacía tal como está** (no cargues datos):

1. `ng build` sin errores y `ng test` en verde. Agrega pruebas con `HttpTestingController`, simulando respuestas
   **con** datos:
   - `checkout-api`: cuerpo `{ lineas }`, encabezado `Idempotency-Key`, `errors[]` por línea, 403 (`forbidden` y
     `email-not-verified`), 409 y `payment: null`;
   - `inscripciones-api`: lista, cancelación, 409 `registration-not-cancellable` y 404 de inscritos;
   - la página de resultado: un caso por estado del pedido, más `?error=pago` y 404.
2. **Carrito**: `/carrito` llama a `POST /carrito/validacion` al abrir y al cambiar una cantidad (pestaña Network). Con
   la base vacía, un producto guardado de antes aparece como `item-unavailable` y no rompe la página.
3. **Checkout**:
   - sin sesión, «Pagar» lleva al login;
   - con sesión de cliente, «Pagar» envía `POST /checkout` con `Idempotency-Key`. Con la base vacía no habrá ítems
     válidos: verifica que el error se muestre bien. El viaje completo a Webpay lo probaré yo con datos;
   - con sesión de vendedor, «Pagar» muestra el aviso de que las compras son para cuentas de cliente.
4. **`/pago/resultado?pedido=PP-0000-000000`** muestra «El pedido no existe.» y **`/pago/resultado?error=pago`**
   muestra el mensaje de error.
5. **Perfil** con `cliente@primerplayer.cl`: llama a `/me/inscripciones` y muestra el estado vacío; ya no lee
   reservas de `localStorage`.
6. **`/admin/eventos`** con `vendedor@primerplayer.cl`: «Ver inscritos» de un evento inexistente muestra el 404 sin
   romper la página.
7. `grep` de `TODO(api)`: lista los que quedan y confirma que corresponden solo a la sección 3.

Al final dame un resumen con: archivos creados o modificados, qué quedó conectado, qué sigue local, diferencias entre
este documento y el OpenAPI, y decisiones que tomaste.
