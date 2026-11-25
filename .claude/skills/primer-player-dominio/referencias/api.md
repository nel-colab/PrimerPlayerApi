# Contrato de la API REST

Base `/api/v1`. JSON en `camelCase` compatible con los tipos del frontend. Montos enteros CLP.
Fechas ISO 8601 con zona. OpenAPI publicado por springdoc en `/api/v1/openapi.json`.

## Formatos comunes

Paginación (`?page=0&size=24&sort=precio,asc`; `sort` solo acepta una lista blanca de campos):

```json
{ "content": [], "page": 0, "size": 24, "totalElements": 137, "totalPages": 6 }
```

Error `application/problem+json` (RFC 9457), siempre con `code`:

```json
{
  "type": "https://api.primerplayer.cl/problemas/purchase-limit",
  "title": "Límite de compra superado",
  "status": 409,
  "code": "purchase-limit",
  "detail": "Ya compraste 6 de 8 unidades permitidas de Sobre Black Bolt.",
  "instance": "/api/v1/checkout",
  "requestId": "01JC9Z...",
  "errors": [ { "line": 0, "itemType": "product", "itemId": 42, "code": "purchase-limit", "maxAllowed": 2 } ]
}
```

| `code` | HTTP |
| --- | --- |
| `out-of-stock`, `stock-limit`, `event-full`, `event-past`, `item-unavailable`, `purchase-limit`, `invalid-quantity`, `empty` | 409 (422 para `invalid-quantity` y `empty`) |
| `validation` (con `errors[].field`) | 400 |
| `payments-mismatch`, `receipt-required`, `transfer-proof-required`, `tcg-card-required`, `cash-change-mismatch` | 422 |
| `barcode-taken`, `barcode-immutable`, `version-conflict`, `idempotency-conflict` | 409 |
| `slot-taken`, `request-not-pending` | 409 |
| `product-not-approved`, `review-not-pending`, `review-outdated` | 409 |
| `google-email-mismatch`, `google-account-in-use` | 409 |
| `google-unavailable` | 503 |
| `precondition-required` (falta `If-Match`) | 428 |
| `limit-admin-only`, `forbidden`, `email-not-verified`, `profile-incomplete` | 403 |
| `unauthorized`, `invalid-google-token` | 401 |
| `account-locked` | 423 |
| `rate-limited` (con `Retry-After`) | 429 |

## Endpoints

### Identidad (`identidad`)

| Método | Ruta | Rol | Notas |
| --- | --- | --- | --- |
| POST | `/auth/registro` | público | `{nombre, email, password}` → 201; envía verificación |
| POST | `/auth/verificacion` | público | `{token}` → 204 |
| POST | `/auth/login` | público | `{email, password}` → `{accessToken, expiresIn, user}` + cookie `pp_rt` |
| GET | `/auth/google/config` | público | `{clientId, enabled}` para el botón de Google Identity Services |
| POST | `/auth/google` | público | `{credential}` (ID token de Google) → igual que el login. Vincula por correo o crea CLIENTE sin contraseña |
| POST | `/me/verificacion/google` | sesión | `{credential}` → perfil con `emailVerified: true`; 409 si Google confirma otro correo |
| POST | `/auth/refresh` | cookie `pp_rt` | rota el refresh; reuso → revoca familia; exige `X-Requested-With: pp-web` |
| POST | `/auth/logout` | cookie | 204, revoca familia y borra cookie |
| POST | `/auth/recuperacion` | público | `{email}` → 202 siempre |
| POST | `/auth/restablecimiento` | público | `{token, password}` → 204 |
| GET | `/me` | sesión | `{email, name, role}` con role en minúscula del frontend (`usuario`, `vendedor`, `admin`) |
| PUT | `/me/password` | sesión | `{actual, nueva}` → 204, revoca otros refresh |
| GET/POST/PATCH | `/admin/usuarios[/{id}]` | ADMIN | crear, desactivar, cambiar rol de internos |

### Cliente (`clientes`, `ventas`, `eventos`)

| Método | Ruta | Rol |
| --- | --- | --- |
| GET/PUT | `/me/perfil` | Con sesión (todos los roles) |
| GET/POST/PUT/DELETE | `/me/direcciones[/{id}]` | Con sesión (todos los roles) |
| GET/PUT/DELETE | `/me/favoritos[/{productoId}]` | Con sesión (todos los roles) |
| GET/PUT/DELETE | `/me/alertas-stock[/{productoId}]` | Con sesión (todos los roles) |
| GET | `/me/pedidos`, `/me/pedidos/{codigo}` | Con sesión (todos los roles) |
| GET | `/me/inscripciones` | Con sesión (todos los roles) |
| GET, POST | `/me/solicitudes-cumpleanos` | Con sesión (todos los roles) |
| POST | `/me/solicitudes-cumpleanos/{id}/cancelacion` | Con sesión (todos los roles) |
| POST | `/me/inscripciones/{id}/cancelacion` | Con sesión (todos los roles) |

Recurso de otro cliente → **404** (no revela existencia).

### Catálogo público (`catalogo`, `eventos`)

| Método | Ruta | Notas |
| --- | --- | --- |
| GET | `/categorias` | árbol `{slug, titulo, hijos[]}` |
| GET | `/franquicias` | `string[]` |
| GET | `/productos` | `categoria` (hoja o agrupadora), `q`, `enStock`, `precioMin`, `precioMax`, `tipo[]`, `idioma[]`, `marca[]`, `plataforma[]`, `franquicia[]`, `linea[]`, `sort` = `recomendados` \| `recientes` \| `precio-asc` \| `precio-desc`, `page`, `size` → página + `facets` |
| GET | `/productos/destacados?limit=8` | por popularidad |
| GET | `/productos/{id}`, `/productos/{id}/relacionados` | solo publicados |
| GET | `/eventos`, `/eventos/{id}` | `tipo`, `desde`, `hasta`; incluye `cuposDisponibles`; cumpleaños con `tiendaCerrada: true` |
| GET | `/agenda/ocupados?desde&hasta` | bloques ocupados (máx. 92 días), sin datos de clientes |

Facetas: `{ "tipo": [{"valor":"ETB","cantidad":4}], ..., "precio": {"min": 1990, "max": 649990} }`,
calculadas sobre el resultado filtrado sin la propia faceta.

### Checkout y pagos web (`checkout`, `pagos`)

| Método | Ruta | Rol | Notas |
| --- | --- | --- | --- |
| POST | `/carrito/validacion` | público (sesión opcional) | `{lineas:[{type,itemId,quantity}]}` → precios, stock, cupos, `errors[]` por línea |
| POST | `/checkout` | Con sesión (todos los roles) | `Idempotency-Key` obligatoria → 201 `{pedido, pago:{proveedor, url, token}}` |
| GET, POST | `/pagos/webpay/retorno` | Transbank | 303 → `/pago/resultado?pedido=PP-...` |
| POST | `/admin/pedidos/{codigo}/reembolso` | ADMIN | `{monto, motivo}` |

### Administración (`catalogo`, `inventario`, `media`, `eventos`, `ventas`)

| Método | Ruta | Rol | Notas |
| --- | --- | --- | --- |
| GET | `/admin/productos` | VENDEDOR | `q` (nombre, id o código), `categoria[]`, `estado[]` (publicado, oculto, stock-bajo, agotado), `sort`, `page` |
| GET | `/admin/productos/exportar` | ADMIN | Array JSON con todos los productos activos o XLSX vía `Accept` / `format=excel` |
| POST | `/admin/productos/importar` | ADMIN | `{filas:[...]}` JSON o multipart `archivo` XLSX/CSV; procesa filas individualmente, categoría (slug hoja) obligatoria para crear, reemplaza stock registrando ajuste |
| GET | `/admin/productos/indicadores` | VENDEDOR | `{total, stockBajo, agotados}` |
| POST | `/admin/productos` | VENDEDOR | `imagenes[{archivoId \| url, portada}]`; de VENDEDOR nace `aprobacion=PENDIENTE` sin publicar |
| PUT | `/admin/productos/{id}` | VENDEDOR | `If-Match: <version>`; código de barras y límite ya configurado: solo ADMIN los agrega/cambia. VENDEDOR sobre producto `APROBADO` → **202** (queda en revisión, `cambioPendiente`) |
| PATCH | `/admin/productos/{id}/publicacion` | VENDEDOR | `{publicado}`; ocultar directo; publicar de VENDEDOR → 202 o 409 `product-not-approved` |
| DELETE | `/admin/productos/{id}` | VENDEDOR | borrado lógico; descarta su revisión pendiente |
| GET | `/admin/productos/revisiones` | VENDEDOR | bandeja: `estado` = PENDIENTE (defecto) \| APROBADA \| RECHAZADA \| DESCARTADA \| todas; más antiguas primero |
| GET | `/admin/productos/{id}/revisiones` | VENDEDOR | historial del producto, más reciente primero |
| POST | `/admin/productos/revisiones/{id}/aprobacion` | ADMIN | ALTA: aprueba y publica; EDICION: aplica lo propuesto. 409 `review-not-pending`, `review-outdated` |
| POST | `/admin/productos/revisiones/{id}/rechazo` | ADMIN | `{motivo}` obligatorio. 409 `review-not-pending` |
| POST | `/admin/inventario/{productoId}/ajustes` | VENDEDOR | `{delta, motivo}` → `{stock}` |
| GET | `/admin/inventario/{productoId}/movimientos` | VENDEDOR | kardex paginado |
| GET/PUT | `/admin/inventario/configuracion` | ADMIN | `{umbralStockBajo}` |
| POST | `/admin/media/imagenes` | VENDEDOR | multipart ≤ 5 MB → `{id, url}` |
| POST | `/admin/media/comprobantes` | VENDEDOR | multipart → `{id}` (bucket privado) |
| GET | `/admin/media/comprobantes/{id}` | VENDEDOR | `{url, expiraEn}` firmada 5 min |
| CRUD + PATCH | `/admin/eventos[/{id}][/publicacion]` | VENDEDOR | límite solo ADMIN tras crear |
| GET | `/admin/eventos/{id}/inscritos` | VENDEDOR | |
| GET | `/admin/solicitudes-cumpleanos?estado=` | VENDEDOR | bandeja (PENDIENTE por defecto) |
| POST | `/admin/solicitudes-cumpleanos/{id}/aprobacion` \| `/rechazo` | VENDEDOR | aprobar crea el evento `cumpleanos`; rechazo exige `{motivo}` |
| GET | `/admin/pedidos` | VENDEDOR | `desde, hasta, canal, estado, metodo, vendedor, page` |
| PATCH | `/admin/pedidos/{codigo}/estado` | VENDEDOR | `LISTO_RETIRO` \| `ENTREGADO` |

### POS y caja (`pos`, `caja`)

| Método | Ruta | Rol | Notas |
| --- | --- | --- | --- |
| GET | `/admin/pos/productos/codigo/{codigo}` | VENDEDOR | 404 `item-unavailable` |
| POST | `/admin/pos/validacion` | VENDEDOR | igual que carrito, sin límite por cliente |
| POST | `/admin/pos/ventas` | VENDEDOR | `{lineas[], pagos[], confirmarVueltoDistinto?}` + `Idempotency-Key` → 201 `PurchaseOrder` |
| POST | `/admin/pos/ventas/{codigo}/anulacion` | ADMIN | opcional; repone stock y cupos |
| GET | `/admin/caja/{fecha}` | VENDEDOR | resumen del día |
| PUT | `/admin/caja/{fecha}/cierre` | VENDEDOR | `{fondoInicial, efectivoContado, observaciones}` |
| GET | `/admin/caja/cierres` | VENDEDOR | historial paginado |

### Reportes y auditoría (solo ADMIN)

`GET /admin/reportes/dia?fecha=`, `/por-hora?fecha=`, `/por-dia?mes=2026-11` (también `year` + `month`),
`/por-mes?anio=2026` (también `year`), `/por-dia` y `/por-mes` aceptan `format=excel` o `Accept` XLSX,
`GET /admin/auditoria?entidad=&entidadId=&desde=&hasta=&page=`.

## Ejemplos

`POST /admin/pos/ventas` (pago mixto):

```json
{ "lineas": [ { "type": "product", "itemId": 42, "quantity": 2 } ],
  "pagos": [
    { "method": "cartas-tcg", "amount": 5000,
      "details": { "tcgCard": { "name": "Charizard ex", "code": "OBF-125", "value": 5000 } } },
    { "method": "efectivo", "amount": 6980,
      "details": { "cashReceived": 10000, "changeGiven": 3020 } } ] }
```

`PUT /admin/caja/2026-11-10/cierre` → respuesta (todo calculado en el servidor):

```json
{ "date": "2026-11-10", "openingCash": 50000, "expectedSales": 412870, "cashNet": 134000,
  "expectedInDrawer": 184000, "countedCash": 183500, "difference": -500, "status": "faltante",
  "orderCount": 31, "version": 2, "closedAt": "2026-11-10T21:05:00-03:00", "closedBy": "vendedor@primerplayer.cl" }
```
