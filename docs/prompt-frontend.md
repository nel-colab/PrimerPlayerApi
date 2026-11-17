# Tarea: conectar el frontend Angular a la API real de Primer Player y retirar los servicios dummy

Trabajas en el frontend Angular 22 de Primer Player (tienda de TCG, videojuegos y eventos en San Bernardo, Chile).
Hasta ahora el front usa **servicios dummy** (datos en memoria o mocks) que generaste antes. Ya existe un backend real
(Spring Boot, `primer-player-api`) que implementa parte de esa lógica. Tu tarea:

1. Reemplazar los servicios dummy **solo de las áreas que el backend ya implementa** (lista abajo) por llamadas HTTP reales.
2. Dejar los dummy de las áreas que el backend **aún no tiene**, marcados con `// TODO(api): pendiente en backend`.
3. No cambiar el comportamiento visible de los componentes salvo donde la API lo exija (campos nuevos, errores reales).

## Antes de escribir código

1. Revisa el proyecto: dónde están los servicios dummy, los modelos (`Product`, `Evento`, usuario, etc.), los guards,
   y cómo los componentes consumen esos servicios. Haz un inventario y muéstramelo antes de cambiar nada grande.
2. Levanta y consulta el backend (en el repo `primer-player-api`):
   `docker compose up -d` y `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` → `http://localhost:8080`.
3. **Fuente de verdad del contrato:** `GET http://localhost:8080/api/v1/openapi.json` (Swagger UI en
   `http://localhost:8080/swagger-ui.html`). Si algo de este documento no coincide con el OpenAPI, manda el OpenAPI.
4. Usuarios de prueba (perfil dev): `admin@primerplayer.cl`, `vendedor@primerplayer.cl`, `cliente@primerplayer.cl`,
   todos con contraseña `PrimerPlayer2026!`.

## Conexión

- Usa el **proxy de desarrollo de Angular** para no depender de CORS (el backend no tiene CORS configurado) y para que
  la cookie del refresh funcione como same-origin: `proxy.conf.json` con `"/api": { "target": "http://localhost:8080", "secure": false }`
  y `proxyConfig` en `angular.json` (serve). Base de la API en el front: `/api/v1` (en un `environment`/token de config).
- `provideHttpClient(withInterceptors([...]))` con interceptores funcionales:
  - **auth**: agrega `Authorization: Bearer <accessToken>` a las llamadas a `/api/v1` (no a `/auth/login` ni `/auth/registro`).
  - **refresh en 401**: si una llamada protegida responde `401` → intentar **un** `POST /auth/refresh` (ver §1) y, si
    funciona, repetir la llamada original con el token nuevo; si el refresh falla → limpiar sesión y llevar al login.
    Nunca intentar refresh sobre las propias llamadas a `/auth/*` (evita bucles).
- Llamadas que dependen de la cookie (`/auth/refresh`, `/auth/logout`): `withCredentials: true` (con el proxy no es
  estrictamente necesario, pero déjalo explícito).

## Reglas generales del contrato

- **Montos** en CLP como enteros (`5000`), nunca decimales.
- **Fechas**:
  - La API **responde** ISO 8601 con el desfase de Chile: `"2026-12-12T15:00:00-03:00"`.
  - La API **acepta** `"2026-12-12T15:00"` (lo que entrega un `<input type="datetime-local">`, se interpreta como hora
    de Chile), ISO con zona, o solo fecha `"2026-12-12"` (inicio de ese día) en filtros.
  - En filtros `desde`/`hasta`, `hasta` es **exclusivo**.
  - `fecha` de un producto es solo fecha (`"2026-11-11"`).
- **Paginación** (todas las listas paginadas): `{ content: T[], page, size, totalElements, totalPages }`; parámetros `page` (desde 0) y `size`.
- **Roles** del usuario en el front: `"usuario"` (cliente), `"vendedor"`, `"admin"`.
- **Concurrencia optimista** en el mantenedor de productos y eventos: el `GET`/`POST`/`PUT` devuelve el header
  `ETag: "3"` (también viene `version` en el cuerpo). El `PUT` **exige** `If-Match: "3"` (usa `version` del cuerpo).
  Sin `If-Match` → 428; con versión vieja → 409 `version-conflict` (mostrar "otra persona modificó este registro, recarga").
- **Errores**: siempre `application/problem+json`:
  ```json
  { "type": "...", "title": "...", "status": 409, "detail": "mensaje legible", "instance": "/api/v1/...",
    "code": "slot-taken",
    "errors": [ { "field": "fecha", "message": "..." } ] }
  ```
  Decide por `code` (no por `status` ni `detail`). `errors[]` trae `{field, message}` en `validation` (mapéalo a los
  campos del formulario) o `{line, itemType, itemId, code, maxAllowed}` en errores de líneas de stock/cupos.

| `code` | HTTP | Cuándo |
| --- | --- | --- |
| `validation` | 400 | campos inválidos (`errors[].field`) |
| `unauthorized` | 401 | sin token, token vencido o credenciales malas en login |
| `forbidden` | 403 | rol sin permiso |
| `limit-admin-only` | 403 | un vendedor cambia un límite por usuario ya configurado |
| `item-unavailable` | 404 | producto/evento/solicitud no existe o fue eliminado |
| `email-taken` | 409 | registro con correo existente |
| `barcode-taken` | 409 | código de barras ya usado por otro producto |
| `barcode-immutable` | 409 | un vendedor intenta agregar/cambiar el código de barras de un producto existente (solo admin) |
| `version-conflict` | 409 | `If-Match` con versión vieja |
| `out-of-stock` / `stock-limit` | 409 | ajuste de stock que lo dejaría negativo |
| `slot-taken` | 409 | horario ocupado (cumpleaños / evento dentro de un cumpleaños) |
| `request-not-pending` | 409 | la solicitud de cumpleaños ya fue resuelta |
| `account-locked` | 423 | 5 intentos fallidos de login (15 min) |
| `precondition-required` | 428 | falta `If-Match` |

## 1. Autenticación (`/api/v1/auth`, `/api/v1/me`)

| Método | Ruta | Body | Respuesta |
| --- | --- | --- | --- |
| POST | `/auth/registro` | `{nombre, email, password}` (password 8–72) | 201 `{email, name, role}` (no inicia sesión) |
| POST | `/auth/login` | `{email, password}` | 200 `{accessToken, expiresIn, user: {email, name, role}}` + cookie HttpOnly `pp_rt` |
| POST | `/auth/refresh` | — (usa la cookie) + header **`X-Requested-With: pp-web`** | 200 igual que el login (`{accessToken, expiresIn, user}`) + cookie `pp_rt` **nueva**; 401 `unauthorized` = volver a iniciar sesión; 403 si falta el header |
| POST | `/auth/logout` | — (usa la cookie) | 204; siempre responde 204 |
| GET | `/me` | Bearer | 200 `{email, name, role}` |

Manejo de sesión:
- Guarda el `accessToken` **solo en memoria** (servicio/signal), nunca en `localStorage`/`sessionStorage`.
  `expiresIn` = 900 s (15 min). El refresh token vive en la cookie HttpOnly `pp_rt` (30 días): el front **no** la lee
  ni la escribe, solo la envía.
- **Al iniciar la app** (`provideAppInitializer` o equivalente): llamar a `POST /auth/refresh`. Si responde 200, la
  sesión se restaura (token + usuario) sin pedir login; si responde 401, el usuario queda anónimo (no es un error que
  mostrar). Así una recarga de página no cierra la sesión.
- **Renovación**: además del refresh reactivo en 401 (interceptor), puedes programar un refresh ~1 min antes de
  `expiresIn`. Cualquiera de los dos sirve; el reactivo es obligatorio.
- **Un solo refresh a la vez**: si varias llamadas reciben 401 juntas, todas deben esperar el **mismo** refresh en
  curso (comparte un `Observable`/`Promise` con `shareReplay`), no lanzar uno cada una. El refresh **rota** la cookie:
  dos refresh simultáneos con la misma cookie hacen que el segundo reciba 401.
- **Varias pestañas**: si el refresh responde 401, **reintenta una vez** tras ~500 ms antes de cerrar la sesión. Si otra
  pestaña acababa de refrescar, el navegador ya tiene la cookie nueva y el reintento funciona (el backend da una
  gracia de 10 s para este caso). Si el reintento también da 401, limpiar sesión y llevar al login.
- Si el backend detecta que un refresh ya usado se reutiliza (posible robo), revoca la sesión de ese dispositivo:
  el front solo verá 401 y debe mandar al login.
- Logout: llamar a `/auth/logout` y **además** borrar el token en memoria y cancelar refresh programados (el JWT
  emitido sigue siendo válido hasta que expire).
- Guards: rutas de cliente → `role === 'usuario'`; panel → `'vendedor' | 'admin'`; configuración de inventario → `'admin'`.
- Pendientes en backend (mantener dummy/ocultar): verificación de correo, recuperación y restablecimiento de contraseña,
  `PUT /me/password`, administración de usuarios.

## 2. Catálogo: categorías, franquicias y mantenedor de productos

Públicos:
- `GET /categorias` → `[{slug, titulo, hoja, hijos: [...]}]` (árbol; los productos solo usan categorías `hoja: true`).
- `GET /franquicias` → `string[]`.

Mantenedor (`/admin/productos`, vendedor o admin):

| Método | Ruta | Notas |
| --- | --- | --- |
| GET | `/admin/productos` | `q` (nombre sin tildes, id o código), `categoria` (repetible; agrupadora incluye sus hojas), `estado` (repetible, OR: `publicado`, `oculto`, `stock-bajo`, `agotado`), `sort` (`id`/`nombre`/`precio`/`stock`/`fecha` + `,asc`/`,desc`), `page`, `size` (máx 100) |
| GET | `/admin/productos/indicadores` | `{total, stockBajo, agotados}` |
| GET | `/admin/productos/{id}` | producto + `ETag` |
| POST | `/admin/productos` | 201; el producto parte con **stock 0** |
| PUT | `/admin/productos/{id}` | `If-Match` obligatorio |
| PATCH | `/admin/productos/{id}/publicacion` | `{publicado: boolean}` |
| DELETE | `/admin/productos/{id}` | 204 (borrado lógico) |

Body de POST/PUT (sin `stock`: el stock se carga con un ajuste de inventario, ver §3):
```json
{ "nombre": "Sobre Pokémon Black Bolt", "descripcion": "...", "precio": 4990, "categoria": "tcg-pokemon",
  "tipo": "Sobre", "idioma": "Inglés", "marca": null, "plataforma": null, "franquicia": "Pokémon",
  "linea": "Scarlet & Violet", "codigoBarras": "7801234567894", "limitePorUsuario": 4, "publicado": true,
  "imagenes": [ { "url": "https://...", "portada": true } ] }
```
Respuesta: lo mismo más `id`, `stock`, `fecha`, `imagen` (URL de la portada), `version`, `actualizadoEn`.

Reglas que el formulario debe reflejar:
- `categoria` = slug de una categoría hoja; `franquicia` = nombre existente o `null`.
- `codigoBarras`: EAN-8, UPC-A (12) o EAN-13 con dígito verificador válido. Tras crear, **solo admin** puede agregarlo o
  cambiarlo → para vendedor, deshabilitar el campo en edición.
- `limitePorUsuario`: `null` = ilimitado. Si ya tiene valor, **solo admin** puede cambiarlo → deshabilitar para vendedor.
- Imágenes: máx. 5, exactamente una `portada`, solo URLs `https://` (aún no hay subida de archivos: `TODO(api)` media).
- Al crear un producto con stock inicial: `POST /admin/productos` y luego `POST /admin/inventario/{id}/ajustes`.

Pendiente en backend (mantener dummy): **catálogo público** (`GET /productos`, filtros, facetas, destacados,
relacionados, detalle público), favoritos, alertas de stock, subida de imágenes.

## 3. Inventario (`/admin/inventario`)

| Método | Ruta | Rol | Notas |
| --- | --- | --- | --- |
| POST | `/admin/inventario/{productoId}/ajustes` | vendedor/admin | `{delta, motivo}` (delta ≠ 0, −9999..9999; motivo obligatorio) → `{productoId, stock}` |
| GET | `/admin/inventario/{productoId}/movimientos` | vendedor/admin | kardex paginado: `{id, tipo, cantidad, stockResultante, referencia, motivo, usuarioId, creadoEn}` (más reciente primero; `cantidad` negativa = salida) |
| GET / PUT | `/admin/inventario/configuracion` | **admin** | `{umbralStockBajo}` |

Errores de ajuste: `out-of-stock` / `stock-limit` (409) si el resultado sería negativo; `item-unavailable` (404).

## 4. Eventos y agenda

Tipos (`tipo`, slug): `league-challenge`, `league-cup`, `torneo-tcg`, `prelanzamiento`, `lanzamiento` (ediciones nuevas
de cartas y juegos de mesa), `videojuegos`, `cumpleanos`, `otros`. Actualiza el `<select>` "Tipo de evento".

Público (sin login):
- `GET /eventos` → página de eventos publicados, por fecha ascendente; sin `desde` parte desde ahora ("Próximos
  encuentros"). Filtros: `tipo` (repetible), `desde`, `hasta`, `page`, `size`.
- `GET /eventos/{id}`.
- `GET /agenda/ocupados?desde=YYYY-MM-DD&hasta=YYYY-MM-DD` → `[{inicio, fin, tipo, tiendaCerrada}]` (máx. 92 días).

Respuesta de evento (tarjeta):
```json
{ "id": 1, "titulo": "League Challenge de Noviembre", "tipo": "league-challenge",
  "fecha": "2026-11-14T15:00:00-03:00", "fechaFin": null, "lugar": "Av. Portales Ote. 1126, San Bernardo",
  "precio": 5000, "cuposTotales": 16, "cuposDisponibles": 16, "limitePorUsuario": null,
  "descripcion": "...", "consideraciones": ["Trae tu mazo preparado para jugar."], "imagen": null,
  "tiendaCerrada": false, "publicado": true, "cuposOcupados": null, "version": null }
```
(`cuposOcupados` y `version` solo vienen en las rutas `/admin`.)

**Cumpleaños en la vista pública**: `tipo: "cumpleanos"`, `tiendaCerrada: true`, `cuposTotales`/`cuposDisponibles`
`null`, `precio` 0. Mostrarlo en la misma lista como aviso ("La tienda estará cerrada al público de 18:00 a 21:00 hrs")
**sin** precio, cupos ni botón de compra.

Mantenedor (`/admin/eventos`, vendedor o admin) — formulario "Nuevo evento":

| Método | Ruta | Notas |
| --- | --- | --- |
| GET | `/admin/eventos` | incluye ocultos y pasados; `tipo`, `desde`, `hasta`, `page`, `size` |
| GET | `/admin/eventos/{id}` | + `ETag` |
| POST | `/admin/eventos` | 201 |
| PUT | `/admin/eventos/{id}` | `If-Match` obligatorio; `limitePorUsuario` ya configurado solo lo cambia admin |
| PATCH | `/admin/eventos/{id}/publicacion` | `{publicado}` |
| DELETE | `/admin/eventos/{id}` | 204 (borrado lógico; libera el bloque si era cumpleaños) |

Body (mapeo de campos del formulario):
```json
{ "titulo": "Nombre del evento", "tipo": "league-challenge", "fecha": "2026-11-14T15:00",
  "fechaFin": null, "lugar": "Av. Portales Ote. 1126, San Bernardo", "cuposTotales": 16, "precio": 5000,
  "limitePorUsuario": null, "descripcion": "Descripción breve",
  "consideraciones": ["Cosas a considerar, una por punto"], "imagen": "https://... o null", "publicado": true }
```
- Agrega al formulario un campo **"Hora de término"** (`fechaFin`): obligatorio si `tipo === 'cumpleanos'`, opcional en
  el resto (sin él, el backend asume 3 h para la agenda). Si es cumpleaños, ocultar cupos, precio y límite.
- `consideraciones`: máx. 10 puntos de 200 caracteres. Imagen: por ahora solo URL `https://` (`TODO(api)` media: el
  selector de archivo queda pendiente).
- `slot-taken` (409): el evento cae dentro de un cumpleaños (o el cumpleaños choca con otro evento). Mostrar mensaje en
  el campo de fecha.
- `cuposTotales` no puede bajar de los ya vendidos (400 `validation`, campo `cuposTotales`).

## 5. Cumpleaños (solicitud → aprobación)

Flujo: el cliente solicita un bloque → queda `PENDIENTE` → la tienda aprueba (se crea y publica el evento
`cumpleanos` que reserva el bloque) o rechaza con motivo. El pago se coordina en la tienda (no hay pago web).

Cliente (rol `usuario`):

| Método | Ruta | Body / respuesta |
| --- | --- | --- |
| POST | `/me/solicitudes-cumpleanos` | `{inicio, fin, festejado, invitados (1–200), telefono, comentario?}` → 201 solicitud |
| GET | `/me/solicitudes-cumpleanos` | lista propia (más reciente primero) |
| POST | `/me/solicitudes-cumpleanos/{id}/cancelacion` | solo si está `PENDIENTE` (si no, 409 `request-not-pending`) |

Solicitud: `{id, inicio, fin, festejado, invitados, telefono, comentario, estado, motivoRechazo, eventoId, resueltaEn, creadoEn}`
con `estado` = `PENDIENTE` | `APROBADA` | `RECHAZADA` | `CANCELADA`.

Reglas para el formulario del cliente: anticipación mínima 48 h; duración entre 1 y 6 h; usar
`GET /agenda/ocupados` para mostrar/bloquear horarios tomados; `slot-taken` si el bloque ya está ocupado.
Mostrar `motivoRechazo` cuando esté rechazada.

Tienda (vendedor o admin):

| Método | Ruta | Body |
| --- | --- | --- |
| GET | `/admin/solicitudes-cumpleanos?estado=PENDIENTE` | `estado`: `PENDIENTE` (defecto) \| `APROBADA` \| `RECHAZADA` \| `CANCELADA` \| `todas`; paginado; incluye `clienteId` |
| POST | `/admin/solicitudes-cumpleanos/{id}/aprobacion` | opcional `{titulo?, descripcion?, consideraciones?, publicado?}` (por defecto: "Cumpleaños privado · tienda cerrada") → solicitud con `eventoId` |
| POST | `/admin/solicitudes-cumpleanos/{id}/rechazo` | `{motivo}` obligatorio |

Crea en el panel una bandeja de solicitudes si no existe. Aprobación puede fallar con `slot-taken` (otro bloque ya
aprobado) o `request-not-pending`.

## Lo que sigue siendo dummy (no hay backend todavía)

Catálogo público de productos, carrito y validación de carrito, checkout y Webpay, compra de cupos de eventos,
pedidos (`/me/pedidos`, `/admin/pedidos`), inscripciones e inscritos de un evento, POS, caja, reportes/estadísticas,
perfil y direcciones, favoritos, alertas de stock, subida de imágenes, usuarios internos,
verificación/recuperación de contraseña y cambio de contraseña. Déjalos funcionando con sus mocks, aislados detrás de la misma interfaz de
servicio, con `// TODO(api): pendiente en backend`. El texto "La compra de cupos está disponible para cuentas de
cliente" puede quedarse, pero el botón de compra debe seguir usando el flujo dummy hasta que exista el checkout.

## Criterios de aceptación

- Login con los tres usuarios demo funciona; el menú y los guards respetan el rol.
- Recargar la página mantiene la sesión (refresh al iniciar); pasados 15 min las llamadas siguen funcionando (refresh
  transparente); el logout deja al usuario anónimo también tras recargar.
- El mantenedor de productos crea, edita (con `If-Match`), publica/oculta y elimina contra la API; el stock se ajusta
  con motivo y se ve el kardex; los indicadores del panel vienen de la API.
- El formulario "Nuevo evento" crea/edita eventos reales (incluido `lanzamiento` y cumpleaños con hora de término) y
  "Próximos encuentros" muestra los eventos de `GET /eventos`, con los cumpleaños como aviso de tienda cerrada.
- Un cliente solicita un cumpleaños, lo ve pendiente, el vendedor lo aprueba desde la bandeja y aparece en la agenda.
- Todos los errores se muestran a partir de `code` / `errors[].field`, en español, sin romper la UI.
- No quedan servicios dummy en las áreas conectadas; las pendientes están marcadas con `TODO(api)`.
- `ng build` sin errores y pruebas existentes actualizadas.

Trabaja por áreas (auth → catálogo/inventario → eventos → cumpleaños), mostrando un resumen de cambios al final de
cada una. Si encuentras una diferencia entre este documento, el OpenAPI y lo que el front espera, pregúntame antes
de inventar una regla.
