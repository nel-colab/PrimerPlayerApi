# Prompt para conectar al frontend las funciones pendientes de Primer Player

> Copia el contenido de este documento y entrégalo al agente que trabaja en `PrimerPlayerFront`.
> Backend de referencia: `PrimerPlayerApi` (Spring Boot 4, PostgreSQL). Frontend: Angular 22.

---

## Contexto y objetivo

Trabaja en el repositorio **PrimerPlayerFront**, en `E:\proyectos\PrimerPlayerFront`. La API está en
`E:\proyectos\PrimerPlayerApi`. Conecta las pantallas que todavía usan datos incompletos o locales con los
endpoints ya implementados en el backend. Conserva el diseño y los flujos actuales; reutiliza los servicios,
modelos, componentes y convenciones existentes.

No es una tarea de despliegue. No cambies dominios, Docker, YAML de hosting ni configuración de producción.
No hagas cambios en el backend salvo que encuentres una discrepancia real entre el contrato descrito aquí y
`GET /api/v1/openapi.json`; si ocurre, informa el endpoint y la diferencia antes de inventar un contrato.

### Estado que ya se encontró en el frontend

No rehagas las integraciones que ya existen:

- Sesión, login, registro y refresh: `src/app/core/services/auth.ts` y `auth-interceptor.ts`.
- Catálogo, eventos, cumpleaños, stock e inventario: sus servicios actuales.
- Pedidos de cliente y panel de retiro: `pedidos-api.ts`.
- Checkout con Webpay: `webpay-api.ts` y la página `/pago/resultado`.
- POS: `pos-api.ts` y `pos-sale.ts`.
- Administración de usuarios: `admin-usuarios-api.ts`.
- Subida de comprobantes del POS: `PosApi.subirComprobante()`.

El estado y la documentación pueden estar desactualizados. Antes de empezar, inspecciona el código actual y
`README.md`; no tomes una fila del README como prueba de que una integración falta o existe.

### Objetivo concreto

1. Conectar perfil/datos de cuenta, cambio y recuperación de contraseña, verificación de correo, direcciones,
   favoritos y alertas de stock.
2. Conectar inscripciones del cliente, cancelación y consulta de inscritos para la tienda.
3. Reemplazar el cierre de caja local y los cálculos locales de ventas/reportes por los endpoints de caja y
   reportes.
4. Añadir anulación de ventas POS para ADMIN y una pantalla de consulta de auditoría.
5. Corregir la ruta de subida de imágenes de productos y conectar la carga de imágenes de eventos usando el
   contrato disponible.
6. Usar la validación del carrito de la API antes de iniciar el pago y presentar errores por línea.
7. Actualizar la documentación del frontend para que describa el estado real al terminar.

No elimines funciones todavía necesarias, no limpies `localStorage` del usuario, no cargues datos ni ejecutes
scripts SQL. La integración debe funcionar con una base vacía y mostrar estados vacíos correctamente.

---

## Reglas técnicas obligatorias

- Usa Angular 22 y los patrones existentes de `src/app/core/services/`: `@Service()`, `inject(HttpClient)`,
  `API_BASE` de `src/app/core/config/api.ts`, `Pagina<T>`, RxJS y signals.
- Llama solo a rutas relativas con `API_BASE` (valor `/api/v1`); nunca codifiques `localhost:8080`.
- El interceptor de autenticación ya agrega el Bearer y coordina refresh. No implementes otro interceptor ni
  guardes el access token en `localStorage`.
- Las páginas nuevas que usen API o sesión deben seguir el manejo SSR/client existente en
  `src/app/app.routes.server.ts`.
- Importes en CLP son enteros (`number` en TypeScript, jamás valores decimales). Fechas del backend son ISO 8601;
  las fechas de caja/reportes son días de Chile `America/Santiago`, `YYYY-MM-DD`.
- Maneja errores con `toApiProblem`, `mensajeProblema` y `aplicarErroresCampo` de
  `src/app/core/http/api-problem.ts`. No ocultes errores con listas vacías ni conviertas una respuesta fallida en
  éxito.
- Conserva autorizaciones por rol en `app.routes.ts`, pero recuerda que los permisos reales los aplica el backend:
  CLIENTE para `/me/**`; VENDEDOR/ADMIN en caja y operaciones de tienda; ADMIN en reportes, auditoría y anulación.
- Al subir archivos usa `FormData` con el campo exactamente llamado `archivo`. No establezcas manualmente
  `Content-Type`: el navegador debe agregar el boundary de multipart.
- No confíes en precios, stock, cupos, roles ni montos que entregue el cliente. La API vuelve a validar las
  operaciones de escritura.
- Antes de editar, comprueba `git status` del frontend. Hay archivos de pedidos con cambios locales que deben
  preservarse e integrarse, no revertirse ni sobrescribirse.

---

## Contrato de API disponible

Todas las rutas siguientes se prefijan con `/api/v1`. Los ejemplos muestran JSON; omiten headers HTTP estándar.
El contrato publicado por el backend en `/api/v1/openapi.json` es la fuente final si hubiera una diferencia.

### 1. Perfil y cuenta propia

#### Leer perfil

`GET /me/perfil` (también `GET /me`, que devuelve el mismo perfil). Requiere sesión.

Respuesta:

```json
{
  "email": "cliente@primerplayer.cl",
  "name": "Ash Ketchum",
  "role": "usuario",
  "phone": "+56 9 1234 5678",
  "rut": "12.345.678-5",
  "memberSince": "2026-10-01T12:00:00-03:00",
  "emailVerified": true,
  "mustChangePassword": false
}
```

`phone`, `rut` y `memberSince` pueden ser `null`. El usuario se determina por el token; no envíes un user id.
Usa estos datos en `cuenta/perfil` en vez de los valores fijos `—`. Puedes hacer que `Auth` consulte el perfil al
restaurar sesión, o consultar desde la pantalla, pero no dupliques estados divergentes ni rompas la respuesta
existente del login.

#### Actualizar perfil

`PUT /me/perfil`:

```json
{ "nombre": "Ash Ketchum", "telefono": "+56 9 1234 5678", "rut": "12.345.678-5" }
```

Respuesta: el mismo objeto de perfil de `GET /me/perfil`. El email no se modifica aquí; RUT debe ser válido.
Campos `telefono` y `rut` pueden ser `null` si se quiere guardar sin esos datos.

#### Cambiar contraseña con sesión

`PUT /me/password`:

```json
{ "actual": "ClaveActual123!", "nueva": "ClaveNueva456!" }
```

Éxito: `204 No Content`. La API cierra las demás sesiones. Tras completar el cambio, conserva/comunica que otros
dispositivos deben volver a iniciar sesión. No registres contraseñas en logs, mensajes ni telemetría.

#### Reenviar verificación de correo

`POST /me/verificacion`, sin cuerpo. Éxito: `202 Accepted`; `409 already-verified` si ya verificó.

### 2. Verificación y recuperación de acceso

Estos endpoints son públicos porque se accede desde los enlaces enviados por correo:

| Acción | Request | Éxito |
|---|---|---|
| Verificar correo | `POST /auth/verificacion`, `{ "token": "token-del-enlace" }` | `204 No Content` |
| Solicitar recuperación | `POST /auth/recuperacion`, `{ "email": "cliente@primerplayer.cl" }` | `202 Accepted` siempre; no revela si la cuenta existe |
| Restablecer contraseña | `POST /auth/restablecimiento`, `{ "token": "token-del-enlace", "password": "ClaveNueva456!" }` | `204 No Content` |

El enlace de recuperación vence en una hora. Un token inválido/vencido/usado devuelve `400 invalid-token`.
La nueva contraseña debe tener entre 8 y 72 caracteres. No inicies sesión automáticamente con el token de
verificación o restablecimiento; tras la recuperación, dirige al login y explica que todas las sesiones anteriores
fueron cerradas. Crea rutas accesibles para los enlaces, por ejemplo `/cuenta/verificar?token=...` y
`/cuenta/restablecer?token=...`, con estado de cargando, éxito y error.

### 3. Direcciones, favoritos y alertas de stock

Todos requieren CLIENTE y toman el usuario del token.

#### Direcciones

| Acción | Endpoint | Request/response |
|---|---|---|
| Listar | `GET /me/direcciones` | `Direccion[]` |
| Crear | `POST /me/direcciones` | Request abajo; responde `Direccion` (`201`) |
| Editar | `PUT /me/direcciones/{id}` | Request abajo; responde `Direccion` |
| Eliminar | `DELETE /me/direcciones/{id}` | `204 No Content` |

Request para crear/editar:

```json
{
  "alias": "Casa",
  "calle": "Freire 456",
  "comuna": "San Bernardo",
  "region": "Región Metropolitana",
  "referencia": "Casa azul",
  "principal": true
}
```

Respuesta:

```json
{
  "id": 3,
  "alias": "Casa",
  "calle": "Freire 456",
  "comuna": "San Bernardo",
  "region": "Región Metropolitana",
  "referencia": "Casa azul",
  "principal": true
}
```

Máximo 5 direcciones. La primera queda principal; al marcar otra principal el backend desmarca la anterior.
Las direcciones se guardan para un despacho futuro, pero **no hay despacho a domicilio actualmente**. No las
presentes como opción de entrega del checkout; el retiro en tienda sigue siendo el único método.

#### Favoritos

| Acción | Endpoint | Respuesta |
|---|---|---|
| Listar | `GET /me/favoritos` | `MarcaProducto[]` |
| Agregar | `PUT /me/favoritos/{productoId}` | `204`; operación idempotente |
| Quitar | `DELETE /me/favoritos/{productoId}` | `204` |

#### Alertas de reposición

| Acción | Endpoint | Respuesta |
|---|---|---|
| Listar | `GET /me/alertas-stock` | `MarcaProducto[]` |
| Crear/reactivar | `PUT /me/alertas-stock/{productoId}` | `204`; idempotente |
| Eliminar | `DELETE /me/alertas-stock/{productoId}` | `204` |

Respuesta de favorito/alerta:

```json
{
  "productId": 42,
  "name": "Sobre Pokémon",
  "price": 4990,
  "image": "https://cdn.primerplayer.cl/black-bolt.webp",
  "stock": 0,
  "createdAt": "2026-11-16T10:00:00-03:00",
  "notifiedAt": null
}
```

En favoritos `notifiedAt` normalmente es `null`. En alertas, `notifiedAt: null` significa pendiente; cuando se
repone stock y se envía el correo se informa la fecha. La API devuelve solo productos visibles. Para navegar al
detalle usa `productId`; no trates esta respuesta corta como `Producto` completo.

### 4. Inscripciones a eventos

#### Mis inscripciones

`GET /me/inscripciones`, requiere CLIENTE. Responde una lista, próximas primero:

```json
[
  {
    "id": 81,
    "eventId": 7,
    "eventType": "league-cup",
    "eventTitle": "League Cup",
    "eventDate": "2026-12-12T15:00:00-03:00",
    "eventPlace": "Av. Portales Ote. 1126, San Bernardo",
    "unitPrice": 5000,
    "quantity": 2,
    "total": 10000,
    "status": "CONFIRMADA",
    "orderCode": "PP-2026-000123",
    "reservedAt": "2026-11-16T10:00:00-03:00",
    "cancelledAt": null,
    "cancellable": true
  }
]
```

Estados: `RESERVADA`, `CONFIRMADA`, `CANCELADA`, `EXPIRADA`. Los datos de título/fecha/lugar/precio son el
snapshot de la compra. `cancellable` debe controlar si se muestra/habilita cancelar; no deduzcas elegibilidad
solo con una fecha local.

#### Cancelar inscripción

`POST /me/inscripciones/{id}/cancelacion`, sin cuerpo. Devuelve la inscripción actualizada con el mismo formato.
Puede fallar con `404 item-unavailable`, `409 registration-not-cancellable` o `409 event-past`. La cancelación
libera cupos, **pero no genera reembolso**. Confirma la acción en UI y deja explícito que cualquier devolución
del dinero se gestiona con la tienda.

#### Ver inscritos (VENDEDOR o ADMIN)

`GET /admin/eventos/{eventId}/inscritos`:

```json
{
  "eventId": 7,
  "confirmedSeats": 12,
  "reservedSeats": 2,
  "registrations": [
    {
      "id": 81,
      "customerName": "Ash Ketchum",
      "email": "cliente@primerplayer.cl",
      "quantity": 2,
      "status": "CONFIRMADA",
      "orderCode": "PP-2026-000123",
      "channel": "web",
      "reservedAt": "2026-11-16T10:00:00-03:00",
      "cancelledAt": null
    }
  ]
}
```

Los registros vigentes aparecen primero; cancelados/expirados al final. No es una lista paginada. Añade acceso
desde el mantenedor de eventos, visible para VENDEDOR/ADMIN.

### 5. Caja

Todas las fechas son días de Chile `YYYY-MM-DD`. Requiere VENDEDOR o ADMIN.

#### Obtener resumen del día

`GET /admin/caja/{fecha}`:

```json
{
  "fecha": "2026-11-16",
  "sales": 12,
  "units": 18,
  "total": 185000,
  "storeTotal": 120000,
  "webTotal": 65000,
  "byMethod": { "efectivo": 50000, "debito": 70000, "webpay": 65000 },
  "cashPayments": 3,
  "cashNet": 48000,
  "items": [
    { "type": "product", "itemId": 42, "name": "Sobre Pokémon", "units": 10, "amount": 49900 }
  ],
  "closure": null
}
```

Los campos de resumen usan los nombres JSON indicados (`sales`, `storeTotal`, etc.). `closure` es `null` si el
día no se ha cerrado; de lo contrario tiene el formato de abajo. Ventas web cuentan en los totales y método
`webpay`, pero no en el efectivo de caja. El servidor calcula los montos; no reconstruyas el resumen con pedidos
paginated del frontend.

#### Guardar/cerrar caja

`PUT /admin/caja/{fecha}/cierre`:

```json
{ "fondoInicial": 50000, "efectivoContado": 98000, "observaciones": "Cuadrada" }
```

Respuesta:

```json
{
  "date": "2026-11-16",
  "openingCash": 50000,
  "countedCash": 98000,
  "cashNet": 48000,
  "expectedCash": 98000,
  "difference": 0,
  "status": "cuadrada",
  "totalSales": 120000,
  "orderCount": 12,
  "byMethod": { "efectivo": 50000, "debito": 70000 },
  "notes": "Cuadrada",
  "version": 1,
  "closedBy": "Vendedor",
  "closedAt": "2026-11-16T22:00:00-03:00"
}
```

El backend calcula `cashNet`, `expectedCash = openingCash + cashNet` y `difference = countedCash - expectedCash`.
Volver a cerrar crea una nueva versión e incorpora la anterior al historial. No uses `localStorage` como fuente
persistente del cierre ni calcules `closedBy`/`closedAt` en el cliente.

#### Historial de cierres

`GET /admin/caja/cierres?page=0&size=30`. Responde `Pagina<Cierre>`:

```json
{
  "content": [],
  "page": 0,
  "size": 30,
  "totalElements": 0,
  "totalPages": 0
}
```

Integra resumen, guardado y listado/historial de versiones con la pantalla `/admin/cierre-caja`. Al cambiar día,
vuelve a consultar resumen y cierre; muestra errores del servidor y no inventes un cierre si falla la petición.

### 6. Reportes de venta (solo ADMIN)

Consume la API en vez de sumar `SalesOrders`/pedidos en el browser. Los reportes excluyen ventas inválidas,
anuladas, rechazadas y expiradas. Las fechas son de Chile.

| Vista | Endpoint |
|---|---|
| Resumen del día, variación, pagos y top 5 | `GET /admin/reportes/dia?fecha=2026-11-16` |
| Ventas por hora | `GET /admin/reportes/por-hora?fecha=2026-11-16` |
| Ventas por día del mes | `GET /admin/reportes/por-dia?mes=2026-11` |
| Ventas por mes del año | `GET /admin/reportes/por-mes?anio=2026` |

Ejemplo de reporte diario:

```json
{
  "fecha": "2026-11-16",
  "total": 185000,
  "totalDiaAnterior": 150000,
  "variacion": 23.3,
  "ordenes": 12,
  "ticketPromedio": 15417,
  "montoProductos": 160000,
  "montoEventos": 25000,
  "totalTienda": 120000,
  "totalWeb": 65000,
  "porMetodo": [{ "metodo": "debito", "ordenes": 5, "monto": 70000 }],
  "top5": [{ "tipo": "product", "itemId": 42, "nombre": "Sobre Pokémon", "unidades": 10, "monto": 49900 }]
}
```

`GET /por-hora` responde `Tramo[]`, cada elemento `{ "tramo": 0, "ordenes": 1, "monto": 5000 }`, para las 24
horas (0–23). `GET /por-dia` y `/por-mes` responden:

```json
{
  "periodo": "2026-11",
  "total": 185000,
  "ordenes": 12,
  "ticketPromedio": 15417,
  "tramos": [{ "tramo": 1, "ordenes": 2, "monto": 20000 }],
  "porMetodo": [{ "metodo": "debito", "ordenes": 5, "monto": 70000 }],
  "top5": [{ "tipo": "product", "itemId": 42, "nombre": "Sobre Pokémon", "unidades": 10, "monto": 49900 }]
}
```

Los `tramos` de `/por-dia` son días del mes; los de `/por-mes`, números de mes 1–12. La variación puede ser
`null` si el día anterior no tuvo ventas. No transformes montos en decimales ni asumas que `null` es cero sin
preservar el significado.

### 7. Auditoría (solo ADMIN)

`GET /admin/auditoria` con filtros opcionales:

- `entidad`, por ejemplo `usuario`;
- `entidadId`;
- `desde` (día de Chile o fecha-hora ISO);
- `hasta` (exclusivo);
- `page` (base 0), `size` (por defecto 50).

Respuesta `Pagina<RegistroAuditoria>`:

```json
{
  "content": [
    {
      "id": 123,
      "at": "2026-11-16T10:00:00-03:00",
      "userId": 1,
      "userName": "Admin",
      "action": "usuario.rol-cambiado",
      "entity": "usuario",
      "entityId": "42",
      "before": { "rol": "usuario" },
      "after": { "rol": "vendedor" },
      "ip": "192.0.2.10"
    }
  ],
  "page": 0,
  "size": 50,
  "totalElements": 1,
  "totalPages": 1
}
```

`before`/`after` son JSON sin esquema fijo (y pueden ser `null`); tipa como `unknown | null` y preséntalos de
forma segura, escapando texto y evitando interpretar HTML. Crea una página paginada `/admin/auditoria`, filtros
de entidad/id/fechas y detalle legible. No supongas que todos los tipos de auditoría tienen las mismas claves.
No cambies los roles de acceso.

### 8. Anular una venta POS (solo ADMIN)

`POST /admin/pos/ventas/{codigo}/anulacion`, cuerpo `{}`. Ejemplo:

```http
POST /api/v1/admin/pos/ventas/PP-2026-000123/anulacion
Authorization: Bearer <token>
Content-Type: application/json
```

Éxito: `PedidoResponse`, mismo contrato de pedido que el usado por `Pedido` (`code`, `status`, `channel`, `total`,
`email`, `customerName`, `seller`, `payments`, `createdAt`, `paidAt`, `updatedAt`, `items`). La respuesta refleja
`status: "ANULADO"`. `404 item-unavailable` si no existe; `409 order-invalid-state` si no es venta de mostrador
vigente. La anulación repone stock y cupos; **no devuelve dinero automáticamente**: la devolución se gestiona en
tienda y los pagos quedan como registro.

Agrega una acción de anulación en el detalle/listado de ventas POS, disponible solo para ADMIN, con confirmación,
estado de espera, manejo de error y actualización/refresco de datos al éxito. No muestres la acción a vendedor.
El backend sigue siendo la autoridad de permisos.

### 9. Imágenes de productos y eventos; discrepancia de ruta

El endpoint correcto para subir imágenes es:

`POST /admin/media/imagenes`, `multipart/form-data`, campo `archivo`.

Respuesta:

```json
{
  "id": "7eb25392-c27f-43ee-8f20-d8cf11c6d4bb",
  "url": "https://cdn.primerplayer.cl/black-bolt.webp",
  "mime": "image/webp",
  "bytes": 48231,
  "width": 800,
  "height": 800
}
```

Requiere VENDEDOR/ADMIN; máx. 5 MB; JPEG, PNG o WebP. La ruta que actualmente aparece en
`AdminStockApi.subirImagen()` como `/admin/media/productos` es incorrecta: cámbiala a `/admin/media/imagenes`
y usa el tipo real de respuesta. El endpoint de comprobantes sigue siendo `/admin/media/comprobantes`, no lo
cambies al corregir la imagen de producto.

Para productos, cada entrada `ProductoRequest.imagenes[]` acepta una URL externa o el UUID devuelto al subir:

```json
{
  "url": "https://cdn.primerplayer.cl/black-bolt.webp",
  "portada": true,
  "archivoId": "7eb25392-c27f-43ee-8f20-d8cf11c6d4bb"
}
```

Usa `archivoId` para los uploads de la API; `url` para una URL HTTPS externa. Al leer producto, `url` viene
resuelta. Respeta máximo cinco imágenes y una sola portada. No guardes Data URLs en la entidad ni en `localStorage`.

Para eventos, `POST /admin/media/imagenes` ya permite subir la imagen pública, pero el DTO de evento actualmente
recibe `imagen` como **URL** (no `archivoId`). Por tanto, tras subir, envía `response.url` en `imagen` al guardar
el evento. No agregues `imagenArchivoId` al JSON: el backend actual no lo acepta. La persistencia del evento por
ID de archivo requeriría un cambio de backend aparte y no forma parte de esta integración.

### 10. Validación previa del carrito

`POST /carrito/validacion`, público; si hay sesión cliente toma el límite de compra de esa sesión. No reserva
stock ni cupos. Envíale el contenido actualizado del carrito antes de intentar checkout:

```json
{
  "lineas": [
    { "type": "product", "itemId": 42, "quantity": 2 },
    { "type": "event", "itemId": 7, "quantity": 1 }
  ]
}
```

Respuesta HTTP `200` incluso cuando el carrito es inválido:

```json
{
  "lines": [
    {
      "type": "product",
      "itemId": 42,
      "name": "Sobre Pokémon",
      "quantity": 2,
      "unitPrice": 4990,
      "subtotal": 9980,
      "available": 3,
      "maxAllowed": 8
    }
  ],
  "total": 9980,
  "valid": true,
  "errors": []
}
```

`lines[].available` puede ser `null` cuando el ítem no existe. `maxAllowed: null` significa sin límite o sin
sesión. Cuando `valid` sea `false`, `errors[]` contiene `{ "line": 0, "itemType": "product", "itemId": 42,
"code": "out-of-stock", "maxAllowed": null }`; line es el índice de la línea enviada. Los códigos pueden incluir
`item-unavailable`, `out-of-stock`, `stock-limit`, `event-full`, `event-past`, `purchase-limit` e
`invalid-quantity`. Enlaza errores con la línea correspondiente; usa los datos autoritativos del servidor para
precio/total/disponibilidad.

Llama la validación cuando el cliente solicita revisar/continuar al pago. Si no es válida, muestra qué línea falla
y no inicies Webpay. Después de una validación correcta llama al `WebpayApi.iniciarCheckout()` existente. El
checkout vuelve a validar atómicamente; nunca uses la validación previa como garantía de disponibilidad y no
elimines la última validación del backend.

---

## Modelos y comportamiento de UI por completar

Integra los servicios/pantallas existentes sin duplicar la lógica:

| Área | Código frontend a inspeccionar | Resultado esperado |
|---|---|---|
| Perfil y seguridad de cuenta | `features/public/cuenta/perfil/`, `core/services/auth.ts` | Datos del backend, edición, cambio de contraseña, reenvío de verificación y accesos a los flujos públicos |
| Direcciones/favoritos/alertas | pantalla de perfil y servicios bajo `core/services/` | CRUD/listado y estados vacíos/cargando/error |
| Inscripciones | perfil y `features/public/eventos/` | Lista API, cancelar cuando `cancellable`, sin afirmar reembolso |
| Inscritos | mantenedor `features/admin/eventos/` | Vista de inscritos para VENDEDOR/ADMIN |
| Caja | `features/admin/cierre-caja/`, `core/services/cash-closures.ts` | Resumen/cierre/historial desde API; quitar persistencia de cierres en navegador |
| Reportes | `features/admin/ventas/`, `core/services/sales-orders.ts` | Consultas por rango al backend; no sumar páginas parcialmente cargadas |
| Auditoría | nueva feature/ruta admin | Página paginada solo ADMIN, consulta y filtros |
| Anulación POS | `features/admin/codigo-barras/`, `core/services/pos-api.ts` | Anular venta existente solo ADMIN |
| Imágenes | `core/services/admin-stock-api.ts`, formularios de stock/eventos | Usar endpoint y cuerpo correctos; URL resultante para evento |
| Carrito | `features/public/carrito/`, `core/services/webpay-api.ts` | Validar carrito con API y luego checkout/Webpay existentes |

Antes de crear archivos, busca si ya hay servicio, modelo, ruta o UI en el worktree actual. Reutiliza el código que
ya esté implementado y evita crear copias de una API o estado. No elimines pantallas/estado local hasta que la
versión API esté integrada y los tests la cubran.

---

## Manejo de errores y respuestas

Las fallas regresan `application/problem+json`, usualmente:

```json
{
  "type": "https://api.primerplayer.cl/problemas/order-invalid-state",
  "title": "Estado inválido",
  "status": 409,
  "code": "order-invalid-state",
  "detail": "La operación no está permitida para el estado actual.",
  "instance": "/api/v1/..."
}
```

Errores de validación pueden incluir:

```json
{
  "code": "validation",
  "errors": [{ "field": "nombre", "message": "no debe estar vacío" }]
}
```

Revisa si `ApiErrorCode`/`MENSAJES` ya incluye todos los códigos de arriba; agrega solo los faltantes y pruebas
en `api-problem.spec.ts`. Códigos relevantes para esta tarea incluyen `already-verified`, `invalid-token`,
`registration-not-cancellable`, `event-past`, `item-unavailable`, `order-invalid-state`,
`precondition-required`, `validation`, `unauthorized` y `forbidden`. En el endpoint de carrito los errores vienen
en el cuerpo de una respuesta 200; no los trates como excepción HTTP.

En todas las pantallas distingue carga, vacío, éxito y error. Los endpoints `204` no tienen JSON que leer.
Cancela suscripciones al destruir componentes siguiendo los patrones del proyecto.

---

## Plan de ejecución solicitado

1. Inspecciona `git status`, README, servicios, rutas, modelos y tests del frontend. Reporta brevemente los archivos
   existentes y cambios locales antes de editar; preserva los cambios del usuario.
2. Confirma los contratos con `http://localhost:8080/api/v1/openapi.json` si la API local está disponible.
   Si no está disponible, trabaja con los DTO/código del backend y no mates ni reinicies procesos.
3. Implementa los grupos funcionales anteriores en cambios coherentes. No te detengas tras agregar solo servicios:
   conecta los componentes, navegación y estados de UI.
4. Escribe tests con `HttpTestingController` para URLs, métodos, query params, cuerpos, headers, respuestas `204`,
   paginación y errores. Agrega pruebas de componentes cuando aporten valor.
5. Ejecuta build y tests del frontend desde `E:\proyectos\PrimerPlayerFront`, con la unidad mayúscula para evitar
   el problema conocido de Vitest en Windows:

   ```powershell
   npm.cmd run build
   npm.cmd exec -- ng test --watch=false
   ```

   Si hay fallas, corrige las introducidas por este trabajo y las fallas de tipos en fixtures/tests directamente
   relacionados que bloqueen el runner. No cambies reglas de negocio para hacer pasar pruebas.
6. Revisa las diferencias, busca `TODO(api)` de las funciones ya conectadas y actualiza el README/estado de
   integración para que reflejen hechos comprobados. No marques una función como conectada si solo está creado
   el servicio pero la pantalla aún usa el estado local.
7. Al terminar, entrega resumen por área, endpoints conectados, archivos cambiados, pruebas ejecutadas y cualquier
   limitación que realmente siga pendiente. No hagas commit ni push.

### Fuera de alcance explícito

- Checkout/Webpay, POS de cobro, catálogo, eventos básicos, cumpleaños, inventario, pedidos y administración de
  usuarios ya tienen integración real: no rehacerlos.
- No implementar reembolsos, despacho a domicilio ni cambios al contrato backend de eventos.
- No cambiar CORS, hosting, dominio, Docker, CI/CD ni secretos de producción.
- No cargar datos demo o alterar bases de datos.
- No descartar cambios de trabajo existentes.
