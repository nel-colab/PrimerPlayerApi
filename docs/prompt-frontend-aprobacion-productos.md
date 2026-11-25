# Prompt: aprobación de productos en el panel (autorización previa del ADMIN)

> Copia el contenido de este documento y entrégalo al agente que trabaja en `PrimerPlayerFront`.
> Backend de referencia: `PrimerPlayerApi` (Spring Boot 4). Frontend: Angular 22.

---

## Contexto y objetivo

Trabaja en el repositorio **PrimerPlayerFront** (`E:\proyectos\PrimerPlayerFront`). La API está en
`E:\proyectos\PrimerPlayerApi` y ya implementa todo lo descrito aquí (migración V12). El contrato vigente está en
`GET /api/v1/openapi.json`.

Regla nueva del negocio: **lo que un VENDEDOR crea o cambia en el mantenedor de productos no llega a la tienda (web
ni POS) hasta que un ADMIN lo aprueba.** Lo que hace un ADMIN se aplica directo, como hasta ahora.

Objetivo:

1. Que el mantenedor (`/admin/stock`) muestre el estado de aprobación de cada producto y entienda la respuesta
   **202 Accepted**, que significa "el cambio quedó esperando aprobación".
2. Una bandeja de revisiones donde el ADMIN aprueba o rechaza, y el vendedor ve qué está pendiente y por qué se
   rechazó algo.

No hagas cambios en el backend. Si el contrato de abajo no coincide con el OpenAPI, informa el endpoint y la
diferencia antes de inventar uno. No cambies hosting, dominios ni configuración de producción.

### Lo que ya existe en el frontend y vas a tocar

- `src/app/core/models/producto-admin.ts`: `ProductoAdmin`, `ProductoRequest`.
- `src/app/core/services/admin-stock-api.ts`: `crear`, `actualizar` (con `If-Match`), `cambiarPublicacion`,
  `eliminar`.
- `src/app/features/admin/stock/` (listado), `stock-form/` (crear y editar).
- `src/app/core/http/api-problem.ts`: mensajes por `code`.
- Rutas del panel en `src/app/app.routes.ts` (con `data.roles`).
- Referencia de UI parecida: la bandeja de `features/admin/cumpleanos/solicitudes-cumpleanos` (aprobar y rechazar
  con motivo). Reutiliza su estilo y sus patrones.

## Reglas (lo que hace el servidor)

| Quién / qué | Resultado |
| --- | --- |
| VENDEDOR crea un producto | 201. `aprobacion = "PENDIENTE"` y `publicado = false`, **aunque haya pedido publicar**. Se le puede cargar stock, pero no se vende. |
| VENDEDOR edita un producto `PENDIENTE` o `RECHAZADO` | 200. Se aplica (no está a la venta). Si estaba rechazado, vuelve a `PENDIENTE`. Siempre queda sin publicar. |
| VENDEDOR edita un producto `APROBADO` | **202**. No se aplica: queda como propuesta. El cuerpo trae el producto **sin cambios** y `cambioPendiente = true`. El `ETag` no cambia. La tienda sigue vendiendo lo aprobado. |
| VENDEDOR vuelve a enviar exactamente lo aprobado | 200 y `cambioPendiente = false`: la propuesta pendiente se descarta. |
| VENDEDOR oculta (`publicado: false`) | 200, se aplica directo. |
| VENDEDOR publica un producto `APROBADO` oculto | **202**: queda como propuesta. |
| VENDEDOR publica un producto no aprobado | 409 `product-not-approved`. |
| ADMIN crea, edita, publica u oculta | Directo (200/201). Si publica un producto `PENDIENTE` o `RECHAZADO`, eso lo aprueba. |
| ADMIN aprueba un alta | El producto queda `APROBADO` y **publicado**. |
| ADMIN aprueba una edición | Se aplican los datos propuestos (la versión del producto sube). |
| ADMIN rechaza (motivo obligatorio) | Alta: el producto queda `RECHAZADO` y sin publicar. Edición: la propuesta se descarta y el producto no cambia. |
| Eliminar un producto | Su revisión pendiente queda `DESCARTADA`. |

Hay a lo más **una** revisión pendiente por producto: una nueva edición del vendedor reemplaza la anterior.

## Contrato de la API

Base: `/api/v1`. Errores en `application/problem+json` con campo `code`.

### Campos nuevos en `ProductoAdmin` (todas las respuestas de `/admin/productos/**`)

```json
{
  "id": 42, "nombre": "Sobre Black Bolt", "precio": 4990, "publicado": false,
  "aprobacion": "PENDIENTE",
  "cambioPendiente": false,
  "version": 0
}
```

- `aprobacion`: `"PENDIENTE" | "APROBADO" | "RECHAZADO"`. Solo un producto `APROBADO` puede estar publicado.
- `cambioPendiente`: hay una edición de un vendedor esperando aprobación. Los datos que vienen son los **aprobados**.

### Cambios de estado en endpoints existentes

| Endpoint | Cambio |
| --- | --- |
| `PUT /admin/productos/{id}` | Puede responder **202** (ver reglas). |
| `PATCH /admin/productos/{id}/publicacion` | Puede responder **202** o **409 `product-not-approved`**. |

**Importante:** `HttpClient.put<T>()` entrega el cuerpo igual con 200 o con 202, así que con la llamada actual no se
nota la diferencia. Usa `{ observe: 'response' }` y devuelve algo como
`{ producto: ProductoAdmin; enRevision: boolean }` (`enRevision = res.status === 202`).

### Endpoints nuevos

| Método | Ruta | Rol | Respuesta |
| --- | --- | --- | --- |
| GET | `/admin/productos/revisiones?estado=&page=&size=` | VENDEDOR, ADMIN | `Pagina<Revision>`. `estado` = `PENDIENTE` (por defecto) \| `APROBADA` \| `RECHAZADA` \| `DESCARTADA` \| `todas`. Más antiguas primero. `size` máx. 100. |
| GET | `/admin/productos/{id}/revisiones` | VENDEDOR, ADMIN | `Revision[]`, la más reciente primero. |
| POST | `/admin/productos/revisiones/{revisionId}/aprobacion` | **ADMIN** | `Revision` actualizada (sin cuerpo en la petición). |
| POST | `/admin/productos/revisiones/{revisionId}/rechazo` | **ADMIN** | Petición `{ "motivo": "..." }` (obligatorio, máx. 500). Respuesta: `Revision` actualizada. |

`Pagina` tiene la forma común de la API: `{ content, page, size, totalElements, totalPages }`.

### `Revision`

```json
{
  "id": 7,
  "productoId": 42,
  "tipo": "EDICION",
  "estado": "PENDIENTE",
  "actual": { "...": "ProductoAdmin tal como está hoy" },
  "propuesto": {
    "nombre": "Sobre Black Bolt", "descripcion": null, "precio": 5990, "categoria": "tcg-pokemon",
    "tipo": null, "idioma": null, "marca": null, "plataforma": null, "franquicia": null, "linea": null,
    "codigoBarras": null, "limitePorUsuario": null, "publicado": true,
    "imagen": "https://cdn.test/a.webp",
    "imagenes": [{ "url": "https://cdn.test/a.webp", "portada": true, "archivoId": null }]
  },
  "camposModificados": ["precio"],
  "desactualizada": false,
  "motivoRechazo": null,
  "solicitadoPor": 15,
  "solicitadoEn": "2026-11-24T20:10:00Z",
  "revisadoPor": null,
  "revisadoEn": null
}
```

- `tipo`: `"ALTA"` (producto nuevo) o `"EDICION"` (cambio a un producto aprobado).
- En un **ALTA**, `propuesto` es `null` y `camposModificados` es `[]`: lo que se aprueba es `actual`.
- En una **EDICION**, `camposModificados` usa los nombres de campo del frontend (`limitePorUsuario`, `imagenes`,
  `publicado`, ...).
- `desactualizada = true`: el producto cambió después de proponer la edición. No se puede aprobar (409
  `review-outdated`). Solo se puede rechazar, y el vendedor la vuelve a proponer.
- `solicitadoPor` y `revisadoPor` son ids de usuario. Si quieres mostrar nombres, crúzalos con lo que ya tenga el
  panel; si no hay forma, muestra "Vendedor #15". No pidas un endpoint nuevo por esto.

### Códigos de error nuevos

| Código | HTTP | Mensaje sugerido |
| --- | --- | --- |
| `product-not-approved` | 409 | Este producto aún no está aprobado. Se publicará cuando un administrador lo apruebe. |
| `review-not-pending` | 409 | Esta revisión ya fue resuelta. Actualiza la bandeja. |
| `review-outdated` | 409 | El producto cambió después de proponer esta edición. Recházala y pide que se proponga de nuevo. |

Siguen vigentes `version-conflict`, `barcode-immutable`, `limit-admin-only` y `validation`. En una edición del
vendedor esos errores salen **al proponer**, igual que antes.

## Tareas

### 1. Modelo y servicio

1. `ProductoAdmin`: agrega `aprobacion: 'PENDIENTE' | 'APROBADO' | 'RECHAZADO'` y `cambioPendiente: boolean`.
2. Nuevos tipos `RevisionProducto`, `PropuestaProducto`, `TipoRevision` y `EstadoRevision` (en
   `producto-admin.ts` o en un `revision-producto.ts` aparte).
3. `AdminStockApi.actualizar` y `cambiarPublicacion`: `observe: 'response'` y devuelven `{ producto, enRevision }`.
   Ajusta a todos los que las llaman.
4. Métodos nuevos: `revisiones(estado, page, size)`, `revisionesDe(productoId)`, `aprobar(revisionId)` y
   `rechazar(revisionId, motivo)`.
5. Los tres códigos nuevos en `api-problem.ts`.

### 2. Mantenedor `/admin/stock`

1. **Listado:** una insignia por producto.
   - `PENDIENTE`: "Pendiente de aprobación".
   - `RECHAZADO`: "Rechazado".
   - `cambioPendiente`: "Cambios en revisión".
   - `APROBADO` sin cambios: nada nuevo.
2. **Interruptor publicar/ocultar:**
   - Producto no `APROBADO` y usuario VENDEDOR: deshabilitado, con un tooltip que explique que primero debe
     aprobarlo un administrador.
   - Respuesta 202: no cambies el estado visual a "publicado". Muestra un aviso: "Enviado a aprobación".
3. **Formulario `stock-form`:**
   - Al crear como VENDEDOR, avisa que el producto quedará pendiente de aprobación. No ocultes el checkbox
     "publicado": el servidor lo ignora, pero el texto debe dejar claro que se publicará al aprobarse.
   - Al guardar con 202: cierra o resetea el formulario con el producto **devuelto** (los datos aprobados), muestra
     "Cambios enviados a aprobación" y **no** subas la `version` local (el `ETag` no cambió).
   - Al editar un producto `RECHAZADO`, muestra arriba el `motivoRechazo` de la última revisión
     (`GET /admin/productos/{id}/revisiones`, `[0]`) y avisa que al guardar vuelve a revisión.
   - Si el producto tiene `cambioPendiente`, avisa que hay una propuesta pendiente y que guardar la reemplaza.
     Ofrece "Ver propuesta" (mismo historial).
4. **Detalle o historial (opcional, si hay espacio):** lista de revisiones del producto con estado, fechas y motivo.

### 3. Bandeja de revisiones

Nueva ruta en el panel, **exactamente** `/admin/aprobaciones`, con `data: { roles: ['vendedor', 'admin'] }`. El
backend envía a los administradores un correo con un enlace a esa ruta cuando un vendedor crea un producto o propone
un cambio de precio: si la ruta cambia, el enlace queda roto. Si el usuario no tiene sesión iniciada, el enlace debe
pasar por el login y volver a la bandeja. Agrégala
al menú del panel con un contador de pendientes (`totalElements` de `estado=PENDIENTE&size=1`).

1. Filtro por estado: Pendientes (por defecto), Aprobadas, Rechazadas, Descartadas y Todas. Con paginación.
2. Cada tarjeta muestra:
   - El tipo ("Producto nuevo" o "Edición"), el nombre y la portada de `actual`, quién lo solicitó y cuándo.
   - **ALTA:** los datos principales de `actual` (precio, categoría, código de barras, límite, imágenes, stock).
   - **EDICION:** una tabla "Antes → Después" solo con los campos de `camposModificados`, tomando "antes" de
     `actual` y "después" de `propuesto`. Para `imagenes`, muestra las miniaturas de ambos lados. Formatea el
     precio en CLP (`es-CL`, sin decimales).
   - Si `desactualizada`: una advertencia visible y el botón "Aprobar" deshabilitado.
3. **Acciones (solo ADMIN, `role === 'admin'`):**
   - "Aprobar": confirmación simple y luego `POST .../aprobacion`.
   - "Rechazar": un modal con textarea de motivo obligatorio (máx. 500) y luego `POST .../rechazo`.
   - Tras cualquiera de las dos, quita la tarjeta de "Pendientes" y actualiza el contador.
   - El VENDEDOR ve la bandeja **sin** botones de acción.
4. Con `409 review-not-pending` o `review-outdated`: muestra el mensaje y recarga la bandeja.

## Detalles

- Las fechas vienen en UTC (ISO 8601). Muéstralas en `America/Santiago`.
- No calcules en el frontend si un producto "debería" estar aprobado: usa siempre `aprobacion` y `cambioPendiente`
  del servidor.
- El rol sale de `auth.currentUser()?.role` (`'vendedor' | 'admin'`), como en `stock.ts`.
- Un 403 en aprobar o rechazar (por ejemplo, un vendedor con un botón visible por error) se muestra con el mensaje
  genérico de permisos que ya existe.

## Pruebas

- `AdminStockApi.actualizar` y `cambiarPublicacion`: 200 → `enRevision = false`, 202 → `enRevision = true`, y
  conservan el `If-Match`.
- `AdminStockApi.revisiones`, `revisionesDe`, `aprobar` y `rechazar`: URL, método, parámetros y cuerpo correctos.
- Listado: insignias según `aprobacion` y `cambioPendiente`; interruptor deshabilitado para un vendedor con un
  producto no aprobado.
- `stock-form`: con 202 muestra el aviso y no sube la versión; un producto `RECHAZADO` muestra el motivo.
- Bandeja:
  - Un vendedor no ve los botones.
  - Un admin aprueba y rechaza (el rechazo sin motivo no se envía).
  - Una revisión `desactualizada` deshabilita "Aprobar".
  - La tabla "Antes → Después" muestra solo `camposModificados`.
- Mapeo de los tres códigos nuevos a los mensajes de la tabla.

Al terminar: `ng build` sin errores y `ng test --watch=false` en verde. Resume qué archivos cambiaste y qué
quedó pendiente.
