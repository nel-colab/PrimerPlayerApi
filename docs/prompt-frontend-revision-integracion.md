# Prompt para el frontend — Revisión del estado de la integración (solo diagnóstico)

> Copia todo lo que está debajo de la línea y entrégaselo al agente (Claude Code) que trabaja en
> `E:\proyectos\PrimerPlayerFront`. **Este prompt no implementa nada**: solo pide revisar el código del front y
> responder con una tabla de qué está conectado a la API y qué falta.

---

## Tu tarea

Eres el agente del frontend de **Primer Player** (Angular 22, repo `E:\proyectos\PrimerPlayerFront`).
El backend (Spring Boot, repo `E:\proyectos\primer-player-api`) ya está **completo**: tiene sus 14 módulos y
81 endpoints. Necesito saber, **pantalla por pantalla**, qué parte del front ya consume la API real y qué sigue
usando datos locales (mocks, `localStorage`, arreglos en memoria, servicios "fake"), o simplemente no existe.

### Reglas estrictas

1. **No modifiques ningún archivo.** Esta tarea es solo de lectura y diagnóstico.
2. **No ejecutes scripts de base de datos, no cargues datos de prueba y no llames endpoints que escriban**
   (POST/PUT/PATCH/DELETE). Si el backend está levantado, como mucho puedes leer el contrato en
   `GET http://localhost:8080/api/v1/openapi.json`.
3. Ignora la carpeta `E:\proyectos\primerPlayer`: es una versión vieja del front.
4. Responde **solo** con lo que se pide en «Formato de la respuesta». Sin propuestas de código.

### Cómo revisar

Por cada fila de la lista de abajo:

1. Busca la pantalla o el componente correspondiente (rutas en `app.routes.ts` o equivalente, componentes y
   servicios).
2. Revisa si el servicio que la alimenta llama a la URL indicada con `HttpClient` (directo o mediante un
   servicio de API común), o si devuelve datos fijos o locales.
3. Revisa además estos puntos transversales:
   - la URL base `/api/v1` (proxy de Angular o `environment`);
   - el interceptor que agrega `Authorization: Bearer <accessToken>`;
   - la renovación con `POST /auth/refresh`: va con la cookie `pp_rt`, `withCredentials: true` y el encabezado `X-PP-Web: 1`;
   - el manejo de errores `application/problem+json` leyendo el campo `code`.
4. Clasifica cada fila con la leyenda.

---

## Contrato del backend (lo que ya existe)

Todo va bajo `/api/v1`. Las fechas viajan en ISO 8601 con la zona de Chile y los montos en CLP como enteros.
Los errores llegan como `application/problem+json`, con un campo `code` en kebab-case.
Roles: público, **CLIENTE** (en el front, `usuario`), **VENDEDOR** y **ADMIN**.
El detalle exacto de cada request y response está en `GET /api/v1/openapi.json`.

### 1. Sesión y cuenta

| Función | Endpoints | Acceso |
|---|---|---|
| Registro, login, renovación y cierre de sesión | `POST /auth/registro`, `/auth/login`, `/auth/refresh`, `/auth/logout` | Público |
| Usuario de la sesión | `GET /me` → `{email, name, role, phone, rut, memberSince, emailVerified, mustChangePassword}` | Sesión |
| Perfil (ver/editar teléfono, RUT y nombre) | `GET`/`PUT /me/perfil` | Sesión |
| Cambio de contraseña | `PUT /me/password` | Sesión |
| Reenviar mi correo de verificación | `POST /me/verificacion` | Sesión |
| Verificar correo (página `/cuenta/verificar?token=`) | `POST /auth/verificacion` | Público |
| Olvidé mi contraseña | `POST /auth/recuperacion` (siempre responde 202) | Público |
| Restablecer contraseña (página `/cuenta/restablecer?token=`) | `POST /auth/restablecimiento` | Público |

### 2. Catálogo y eventos públicos

| Función | Endpoints | Acceso |
|---|---|---|
| Categorías y franquicias | `GET /categorias`, `/franquicias` | Público |
| Tienda: búsqueda con filtros, orden, paginación y facetas | `GET /productos` | Público |
| Destacados (home) | `GET /productos/destacados` | Público |
| Detalle y relacionados | `GET /productos/{id}`, `/productos/{id}/relacionados` | Público |
| Eventos y detalle | `GET /eventos`, `/eventos/{id}` | Público |
| Agenda (horarios ocupados) | `GET /agenda/ocupados` | Público |

### 3. Compra web

| Función | Endpoints | Acceso |
|---|---|---|
| Validación del carrito (precios, stock, cupos, límite por cliente) | `POST /carrito/validacion` | Público (con sesión valida el límite) |
| Checkout (reserva de 15 min y luego Webpay) | `POST /checkout` con encabezado `Idempotency-Key` (UUID) | CLIENTE con correo verificado |
| Retorno de Webpay (lo llama Transbank, no el front) | `GET`/`POST /pagos/webpay/retorno` → redirige a `/pago/resultado` | Público |
| Página de resultado del pago | ruta del front `/pago/resultado` (usa `GET /me/pedidos/{codigo}`) | CLIENTE |
| Compra de cupos de eventos | vía `/carrito/validacion` y `/checkout` (líneas `type: "event"`) | CLIENTE |

### 4. Área del cliente (`/me`, solo CLIENTE)

| Función | Endpoints |
|---|---|
| Mis pedidos (con `payments`) | `GET /me/pedidos`, `/me/pedidos/{codigo}` |
| Mis inscripciones y cancelación | `GET /me/inscripciones` · `POST /me/inscripciones/{id}/cancelacion` |
| Solicitudes de cumpleaños | `GET`/`POST /me/solicitudes-cumpleanos` · `POST /me/solicitudes-cumpleanos/{id}/cancelacion` |
| Direcciones (máximo 5) | `GET`/`POST /me/direcciones` · `PUT`/`DELETE /me/direcciones/{id}` |
| Favoritos | `GET /me/favoritos` · `PUT`/`DELETE /me/favoritos/{productoId}` |
| Alertas de stock | `GET /me/alertas-stock` · `PUT`/`DELETE /me/alertas-stock/{productoId}` |

### 5. Panel de la tienda (VENDEDOR y ADMIN, salvo lo indicado)

| Función | Endpoints | Acceso |
|---|---|---|
| Mantenedor de productos | `GET`/`POST /admin/productos` · `GET`/`PUT`/`DELETE /admin/productos/{id}` · `PATCH /admin/productos/{id}/publicacion` · `GET /admin/productos/indicadores` | VENDEDOR, ADMIN |
| Imágenes de productos (subida a MinIO) | `POST /admin/media/imagenes` (multipart, campo `archivo`) → `archivoId`, que se envía en `imagenes[].archivoId` del producto | VENDEDOR, ADMIN |
| Inventario | `POST /admin/inventario/{id}/ajustes` · `GET /admin/inventario/{id}/movimientos` · `GET`/`PUT /admin/inventario/configuracion` | VENDEDOR, ADMIN (cambiar el límite: solo ADMIN) |
| Eventos (admin) | `GET`/`POST /admin/eventos` · `GET`/`PUT`/`DELETE /admin/eventos/{id}` · `PATCH /admin/eventos/{id}/publicacion` | VENDEDOR, ADMIN |
| Inscritos de un evento | `GET /admin/eventos/{id}/inscritos` | VENDEDOR, ADMIN |
| Solicitudes de cumpleaños (tienda) | `GET /admin/solicitudes-cumpleanos` · `POST …/{id}/aprobacion`, `…/{id}/rechazo` | VENDEDOR, ADMIN |
| Panel de pedidos y retiro en tienda | `GET /admin/pedidos` · `PATCH /admin/pedidos/{codigo}/estado` | VENDEDOR, ADMIN |
| POS: lectura por código de barras (*) | `GET /admin/pos/productos/codigo/{codigo}` | VENDEDOR, ADMIN |
| POS: validar venta | `POST /admin/pos/validacion` | VENDEDOR, ADMIN |
| POS: venta con pago mixto (efectivo con vuelto, débito/crédito con boleta, transferencia con comprobante, carta TCG) | `POST /admin/pos/ventas` con `Idempotency-Key` | VENDEDOR, ADMIN |
| POS: comprobante de transferencia | `POST /admin/media/comprobantes` (multipart `archivo`) · `GET /admin/media/comprobantes/{id}` (URL firmada por 5 min) | VENDEDOR, ADMIN |
| POS: anulación de venta | `POST /admin/pos/ventas/{codigo}/anulacion` | **ADMIN** |
| Cierre de caja | `GET /admin/caja/{fecha}` · `PUT /admin/caja/{fecha}/cierre` · `GET /admin/caja/cierres` | VENDEDOR, ADMIN |
| Reportes y estadísticas | `GET /admin/reportes/dia?fecha=`, `/por-hora?fecha=`, `/por-dia?mes=YYYY-MM`, `/por-mes?anio=` | **ADMIN** |
| Auditoría | `GET /admin/auditoria` | **ADMIN** |
| Administración de usuarios | `GET /admin/usuarios` (q, rol, estado, page, size) · `GET /admin/usuarios/indicadores` · `PATCH …/{id}/rol` · `POST`/`DELETE …/{id}/baneo` · `POST`/`DELETE …/{id}/lista-negra` · `POST …/{id}/restablecimiento-password`, `…/{id}/verificacion`, `…/{id}/cierre-sesiones` | **ADMIN** |

(*) **El lector de códigos de barras no es un dispositivo que haya que integrar.** Funciona como un teclado:
escribe el código en el campo que tiene el foco y, a lo más, presiona Enter. Basta con un input con foco que, al
recibir Enter, haga el `GET` de arriba.

### Códigos de error que el front debería manejar

`validation`, `unauthorized`, `forbidden`, `email-taken`, `account-locked`, `account-banned` (423),
`blacklisted` (403), `email-not-verified`, `invalid-token`, `already-verified`, `out-of-stock`, `purchase-limit`,
`event-full`, `item-unavailable`, `idempotency-conflict`, `precondition-required`, `payment-provider-error`,
`payments-mismatch`, `order-invalid-state`, `registration-not-cancellable`, `request-not-pending`, `slot-taken`,
`barcode-taken`, `barcode-immutable`, `limit-admin-only`, `version-conflict`, `storage-unavailable`,
`self-action`, `last-admin`.

---

## Formato de la respuesta

Responde con **una sola tabla** que tenga exactamente estas columnas y esta leyenda. Debe tener una fila por cada
función de las secciones 1 a 5, más una fila por cada punto transversal: URL base, interceptor, refresh y errores.

| Pantalla / función | Backend | Frontend | Qué falta por conectar |
|---|---|---|---|
| Login, registro, sesión | ✅ | ✅ API | — |
| Cierre de caja | ✅ | ⚠️ local | `caja.service.ts` usa `localStorage`; falta llamar a `GET /admin/caja/{fecha}` y `PUT …/cierre` |
| Página `/cuenta/verificar` | ✅ | ❌ no existe | Crear la ruta y llamar a `POST /auth/verificacion` |
| … | … | … | … |

**Leyenda de la columna Frontend:**
- ✅ API: usa el endpoint real.
- 🟡 parcial: usa la API solo para una parte; en «Qué falta» se dice cuál.
- ⚠️ local: usa datos locales, mocks o memoria.
- ❌ no existe: no hay pantalla ni servicio.
- — : no aplica, como el retorno de Webpay, que no pasa por el front.

La columna **Backend** queda en ✅ en todas las filas, porque todo existe. Si encuentras algo que el front necesita
y **no** está en este contrato, agrégalo como fila aparte con Backend = ❓ y explica qué esperaba la pantalla.

En la columna **«Qué falta por conectar»**, sé concreto: indica el archivo (con su ruta relativa) y qué endpoint
falta o qué está mal (URL distinta, campos con otros nombres, rol del front mal mapeado, falta
`Idempotency-Key`, no lee `code` del error, etc.).

Después de la tabla agrega solo:

1. **Resumen en números:** cuántas filas hay en ✅, 🟡, ⚠️ y ❌.
2. **Diferencias de contrato:** una lista corta de los campos o rutas que el front espera con otra forma que
   la del `openapi.json`, si encontraste alguna.
