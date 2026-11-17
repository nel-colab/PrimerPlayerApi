# Tarea: conectar el catálogo público y los pedidos del frontend a la API real

Trabajas en **PrimerPlayerFront** (`E:\proyectos\PrimerPlayerFront`), el frontend Angular 22 de Primer Player, una tienda
de TCG, figuras, videojuegos y eventos en San Bernardo, Chile. El backend real es **primer-player-api**
(`E:\proyectos\primer-player-api`, Spring Boot 4 + PostgreSQL).

El backend acaba de publicar dos módulos nuevos:

1. **Catálogo público**: búsqueda de productos con filtros, facetas, destacados, detalle y relacionados.
2. **Pedidos**: "Mis pedidos" del cliente y un panel de pedidos para la tienda, con el flujo de **retiro en tienda**.

Tu tarea es reemplazar la lógica local o vacía del frontend por llamadas a esos endpoints, sin romper lo que ya
funciona. Lee este documento completo antes de escribir código. No tienes skills disponibles: todo lo que necesitas
está aquí o en el código del proyecto.

---

## 0. Reglas generales (no negociables)

- **No cambies el comportamiento visible** salvo donde este documento lo pida. Respeta los estilos, componentes y
  textos actuales.
- **Sigue los patrones que ya existen** en `src/app/core/services/`. Los servicios conectados a la API son el modelo
  a imitar: `events-api.ts`, `catalogo-api.ts`, `admin-stock-api.ts`, `admin-events-api.ts` y `cumpleanos-api.ts`.
  Usan:
  - el decorador `@Service()` de Angular 22,
  - `inject(HttpClient)` o `httpResource`,
  - el token `API_BASE` y el tipo `Pagina<T>` de `src/app/core/config/api.ts`,
  - signals (`signal`, `computed`, `linkedSignal`, `toSignal`).
- **Errores**: la API responde `application/problem+json` con un campo `code`. Usa los helpers que ya existen en
  `src/app/core/http/api-problem.ts` (`toApiProblem`, `mensajeProblema`, `aplicarErroresCampo`). No inventes otro
  manejo de errores.
- **Autenticación**: el interceptor `src/app/core/interceptors/auth-interceptor.ts` ya agrega el `Bearer` y renueva
  la sesión ante un 401. No toques la autenticación.
- **Proxy**: `proxy.conf.json` ya envía `/api` a `http://localhost:8080`. Usa siempre rutas relativas con `API_BASE`
  (`/api/v1`), nunca `http://localhost:8080`.
- **SSR**: el proyecto tiene renderizado en servidor (`app.routes.server.ts`). Antes de crear un servicio nuevo, revisa
  cómo lo resuelve `events-api.ts` y haz lo mismo.
- **Montos**: enteros en pesos chilenos (CLP), sin decimales.
- **Fechas**: la API responde ISO 8601 con el desfase de Chile, por ejemplo `"2026-11-14T15:00:00-03:00"`.
- **No borres** los servicios que siguen siendo locales: carrito, checkout, POS, caja, ventas, reportes y reservas de
  eventos. El backend todavía no tiene esos módulos. Ver la sección 6.
- **Marca con `// TODO(api): pendiente en backend — <qué falta>`** todo lo que quede local, y **elimina esos `TODO`**
  en lo que conectes.
- Antes de cambiar algo grande, haz un **inventario** (sección 1) y muéstramelo.

---

## 1. Antes de escribir código

1. **Levanta el backend** (en `E:\proyectos\primer-player-api`, con Git Bash):
   ```bash
   docker compose up -d
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```
   La API queda en `http://localhost:8080`. El contrato exacto está en `http://localhost:8080/api/v1/openapi.json`
   y en Swagger UI (`http://localhost:8080/swagger-ui.html`). **Si algo de este documento no coincide con el OpenAPI,
   manda el OpenAPI**: avísame la diferencia.

2. **No toques la base de datos.** No ejecutes `scripts/db/reiniciar-datos.sh` (ni con `--entrega`), ni SQL, ni
   cargues datos de ninguna forma. La base está vacía a propósito: la tienda se entrega sin datos de prueba. Tu tarea
   es **solo conectar los servicios**. Con la base vacía, los endpoints responden listas vacías: verifica eso y los
   estados vacíos de cada pantalla. Si el dueño del proyecto carga datos para probar, lo hará él.

3. **Cuentas** (perfil `dev`; contraseña `PrimerPlayer2026!`): `cliente@primerplayer.cl` (CLIENTE),
   `vendedor@primerplayer.cl` (VENDEDOR) y `admin@primerplayer.cl` (ADMIN). La app las crea al arrancar si no existen.

4. **Haz un inventario y muéstramelo** antes de cambiar nada grande:
   - quién usa `ProductsApi` (`porId`, `porCategoria`, `masVendidos`) y `ProductStore`;
   - quién usa `PurchaseHistory` y `SalesOrders`;
   - qué páginas cambian y cuáles quedan igual.

   Punto de partida, no exhaustivo: `app.routes.ts` (resolver `tituloProducto`), `features/public/home/home.ts`,
   `features/public/catalogo/catalogo.ts`, `features/public/producto-detalle/producto-detalle.ts`,
   `features/public/cuenta/perfil/perfil.ts`, `core/services/cart.ts`, `core/services/pos-sale.ts` y
   `features/admin/codigo-barras/codigo-barras.ts`.

---

## 2. Contrato: catálogo público (sin login)

Los 4 endpoints son públicos: funcionan sin sesión y también con cualquier sesión. Solo devuelven productos
**publicados y no eliminados**.

### 2.1 Forma de un producto

Coincide con la interfaz `Product` de `src/app/core/models/product.ts`. No trae `publicado` ni `codigoBarras`.

```json
{
  "id": 42,
  "nombre": "Elite Trainer Box Phantasmal Flames",
  "descripcion": "Texto con formato simple:\n\n## Detalles\n- Producto: ...",
  "precio": 59990,
  "categoria": "tcg-pokemon",
  "tipo": "cajas",
  "idioma": "Inglés",
  "marca": null,
  "plataforma": null,
  "franquicia": "Pokémon",
  "linea": "TCG",
  "limitePorUsuario": 2,
  "stock": 3,
  "popularidad": 87,
  "fecha": "2026-10-28",
  "imagen": "https://cdn.ejemplo.cl/portada.webp",
  "imagenes": ["https://cdn.ejemplo.cl/portada.webp", "https://cdn.ejemplo.cl/2.webp"]
}
```

| Campo | Significado |
|---|---|
| `limitePorUsuario` | `null` = sin límite por cliente |
| `stock` | `0` = agotado. Úsalo para "Agotado" o "Últimas unidades" como hoy |
| `fecha` | Fecha de ingreso (`YYYY-MM-DD`), base del orden "recientes" |
| `imagen` | URL de la portada, o `null` si no tiene imágenes |
| `imagenes` | Todas las URLs, con **la portada primero** (máximo 5) |
| `descripcion` | Puede traer `## títulos` y `- viñetas`. Muéstrala igual que hoy |

### 2.2 `GET /api/v1/productos`: búsqueda con filtros y facetas

**Parámetros** (todos opcionales):

| Parámetro | Tipo | Notas |
|---|---|---|
| `categoria` | slug | Hoja (`tcg-pokemon`) o agrupadora (`figuras` incluye todas sus hojas). `todos` = sin filtro. Un slug inexistente devuelve una página vacía, no un error |
| `q` | texto | Busca en el nombre sin importar tildes ni mayúsculas |
| `enStock` | `true`/`false` | Solo productos con stock > 0 |
| `precioMin`, `precioMax` | entero CLP | `precioMin` ≤ `precioMax`, si no responde 400 |
| `tipo`, `idioma`, `marca`, `plataforma`, `franquicia`, `linea` | texto, **repetible** | `?tipo=sobres&tipo=cajas`. Se combinan con **O** dentro de la misma faceta y con **Y** entre facetas distintas. Valor exacto |
| `sort` | texto | `recomendados` (por defecto), `recientes`, `precio-asc`, `precio-desc`. Cualquier otro valor responde 400 |
| `page` | entero | **Base 0** |
| `size` | entero | 1 a 100 (por defecto 24) |

Para los parámetros repetibles usa `HttpParams.append` en un ciclo, igual que `events-api.ts` con `tipo`. No uses
valores separados por coma.

**Respuesta** (página estándar + `facets`):

```json
{
  "content": [ /* productos con la forma de 2.1 */ ],
  "page": 0,
  "size": 20,
  "totalElements": 37,
  "totalPages": 2,
  "facets": {
    "tipo":       [{ "valor": "sobres", "cantidad": 12 }, { "valor": "cajas", "cantidad": 4 }],
    "idioma":     [{ "valor": "Español", "cantidad": 10 }],
    "marca":      [],
    "plataforma": [],
    "franquicia": [{ "valor": "Pokémon", "cantidad": 16 }],
    "linea":      [{ "valor": "TCG", "cantidad": 16 }],
    "precio":     { "min": 2990, "max": 189990 }
  }
}
```

- **Cada faceta se calcula sobre el resultado filtrado SIN su propio filtro.** Si el usuario marcó `tipo=sobres`,
  la faceta `tipo` sigue mostrando `cajas` con su cantidad, para que pueda sumar más tipos. Es el mismo
  comportamiento que hoy implementa `filtrar(omitir)` en `catalogo.ts`.
- Los valores vienen ordenados por cantidad descendente. Las facetas sin valores llegan como `[]`.
- `precio.min` y `precio.max` son `null` si no hay resultados. Úsalos como límites del filtro de precio.
- Las claves de `facets` son **las mismas** que `FACETAS` en `catalogo.ts`: `franquicia`, `linea`, `tipo`, `idioma`,
  `plataforma` y `marca`.

### 2.3 `GET /api/v1/productos/destacados?limit=8`

Devuelve un arreglo (no una página) de los productos más populares. `limit` va de 1 a 24; por defecto, 8.

### 2.4 `GET /api/v1/productos/{id}`

Detalle de un producto. Si no existe, está oculto o fue eliminado, responde **404** con `code: "item-unavailable"`.
Muestra el mismo "Producto no encontrado" que hoy.

### 2.5 `GET /api/v1/productos/{id}/relacionados?limit=4`

Arreglo de hasta `limit` productos (1 a 12; por defecto 4): primero los de la misma categoría, luego los de la misma
franquicia, con stock antes que agotados. Nunca incluye el propio producto. Si el producto base no es visible,
responde 404 `item-unavailable`.

### 2.6 Errores posibles

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `validation` | `sort` inválido, `precioMin > precioMax`, `size` fuera de 1–100, `limit` fuera de rango. Trae `errors[].field` |
| 404 | `item-unavailable` | Producto inexistente, oculto o eliminado |

---

## 3. Contrato: pedidos

### 3.1 Forma de un pedido

Usa las mismas claves que la interfaz `PurchaseOrder` de `src/app/core/services/purchase-history.ts`, más el estado
y las fechas:

```json
{
  "code": "PP-2026-000123",
  "status": "LISTO_RETIRO",
  "channel": "web",
  "total": 16980,
  "email": "cliente@primerplayer.cl",
  "customerName": "Cliente Demo",
  "seller": null,
  "createdAt": "2026-11-12T19:30:00-03:00",
  "paidAt": "2026-11-12T19:32:00-03:00",
  "updatedAt": "2026-11-13T10:05:00-03:00",
  "items": [
    { "type": "product", "itemId": 42, "name": "Sobre Pokémon Black Bolt", "quantity": 2, "unitPrice": 4990, "subtotal": 9980 },
    { "type": "event",   "itemId": 7,  "name": "League Cup Pokémon TCG",   "quantity": 1, "unitPrice": 7000, "subtotal": 7000 }
  ]
}
```

| Campo | Notas |
|---|---|
| `status` | `PENDIENTE_PAGO`, `PAGADO`, `LISTO_RETIRO`, `ENTREGADO`, `RECHAZADO`, `EXPIRADO`, `ANULADO`, `REEMBOLSADO` |
| `channel` | `web` (compra en la página) o `tienda` (venta en el mostrador) |
| `seller` | Nombre del vendedor; solo en ventas de mostrador, `null` en web |
| `paidAt` | `null` si no se pagó |
| `items[].name` / `unitPrice` | Copia del momento de la compra: no cambian si después cambia el producto |
| `payments` | **Todavía no viene.** Los pagos llegarán con un módulo futuro. No lo uses ni lo exijas |

**Etiquetas sugeridas** para mostrar el estado (agrégalas en un solo lugar, por ejemplo en el modelo):

| `status` | Etiqueta | Color sugerido |
|---|---|---|
| `PENDIENTE_PAGO` | Pendiente de pago | amarillo |
| `PAGADO` | Pagado · en preparación | azul |
| `LISTO_RETIRO` | Listo para retiro | verde |
| `ENTREGADO` | Entregado | gris |
| `RECHAZADO` | Pago rechazado | rojo |
| `EXPIRADO` | Reserva vencida | gris |
| `ANULADO` | Anulado | rojo |
| `REEMBOLSADO` | Reembolsado | morado |

Crea una interfaz nueva `Pedido` (o `PedidoApi`) en `src/app/core/models/` con esta forma. **No modifiques
`PurchaseOrder`**: el carrito y el POS locales todavía lo usan.

### 3.2 `GET /api/v1/me/pedidos?page=0&size=10` (solo CLIENTE)

Página estándar (`content`, `page`, `size`, `totalElements`, `totalPages`) con los pedidos **del cliente que tiene la
sesión**, más recientes primero. El backend saca el cliente del token: nunca envíes un id ni un correo. `size` va de
1 a 100; por defecto, 10.

### 3.3 `GET /api/v1/me/pedidos/{codigo}` (solo CLIENTE)

Detalle de un pedido propio. Si el pedido es de otro cliente o no existe, responde **404** `item-unavailable`: el
backend no revela que existe.

### 3.4 `GET /api/v1/admin/pedidos` (VENDEDOR o ADMIN)

Panel de la tienda, más recientes primero. Parámetros opcionales:

| Parámetro | Notas |
|---|---|
| `codigo` | Parte del código, sin importar mayúsculas: `000123` encuentra `PP-2026-000123`. **Es la búsqueda principal del retiro en tienda** |
| `estado` | Repetible: `?estado=PAGADO&estado=LISTO_RETIRO`. Acepta `LISTO_RETIRO` o `listo-retiro` |
| `canal` | `web` o `tienda` |
| `desde`, `hasta` | `YYYY-MM-DD` = días de Chile, **ambos inclusive** (`hasta=2026-11-30` incluye todo el 30). También acepta fecha-hora ISO. `desde` debe ser anterior a `hasta` |
| `vendedor` | Id del vendedor |
| `page`, `size` | Base 0; `size` de 1 a 100 (por defecto 24) |

### 3.5 `PATCH /api/v1/admin/pedidos/{codigo}/estado` (VENDEDOR o ADMIN)

Cuerpo: `{ "estado": "LISTO_RETIRO" }` o `{ "estado": "ENTREGADO" }`. Responde **200** con el pedido actualizado
(forma de 3.1).

Transiciones permitidas, solo para **pedidos web con productos**:

```
PAGADO ──► LISTO_RETIRO ──► ENTREGADO
   └──────────────────────────► ENTREGADO   (el cliente llegó antes de que lo marcaran listo)
```

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `validation` | `estado` distinto de `LISTO_RETIRO` o `ENTREGADO` |
| 404 | `item-unavailable` | El código no existe |
| 409 | `order-invalid-state` | **Código nuevo.** El estado actual no lo permite: sin pagar, ya entregado, anulado, etc. También para pedidos que solo traen inscripciones a eventos (no hay nada que retirar), para ventas de mostrador (se entregan en el momento) y si otro vendedor lo cambió al mismo tiempo |

Agrega `order-invalid-state` a la traducción de `mensajeProblema` en `api-problem.ts`, por ejemplo: *"Este pedido no
puede cambiar a ese estado. Recarga la lista para ver su estado actual."* Ante un 409, recarga el pedido o la lista.

### 3.6 Seguridad (ya aplicada en el backend)

| Endpoint | Sin sesión | CLIENTE | VENDEDOR | ADMIN |
|---|---|---|---|---|
| `/me/pedidos/**` | 401 | ✅ | 403 | 403 |
| `/admin/pedidos/**` | 401 | 403 | ✅ | ✅ |

Las rutas del frontend deben respetar lo mismo con los guards que ya existen.

---

## 4. Tareas

### Tarea A — Catálogo público

**A1. Reescribe `core/services/products-api.ts`** para que use la API. Propuesta de interfaz; ajústala a los
patrones del proyecto:

```ts
export interface FacetaValor { valor: string; cantidad: number; }
export interface FacetasCatalogo {
  tipo: FacetaValor[]; idioma: FacetaValor[]; marca: FacetaValor[];
  plataforma: FacetaValor[]; franquicia: FacetaValor[]; linea: FacetaValor[];
  precio: { min: number | null; max: number | null };
}
export interface PaginaCatalogo extends Pagina<Product> { facets: FacetasCatalogo; }

export interface FiltroCatalogo {
  categoria?: string;          // slug; 'todos' o ausente = sin filtro
  q?: string;
  enStock?: boolean;
  precioMin?: number | null;
  precioMax?: number | null;
  tipo?: string[]; idioma?: string[]; marca?: string[];
  plataforma?: string[]; franquicia?: string[]; linea?: string[];
  sort?: 'recomendados' | 'recientes' | 'precio-asc' | 'precio-desc';
  page?: number;               // base 0
  size?: number;
}

buscar(filtro: FiltroCatalogo): Observable<PaginaCatalogo>
destacados(limit = 8): Observable<Product[]>
obtener(id: number): Observable<Product>              // 404 → error con code item-unavailable
relacionados(id: number, limit = 4): Observable<Product[]>
```

- Mantén el mapa `TITULOS` exportado: otras páginas lo usan.
- Agrega una **caché por id**, igual que `EventsApi` (`registrar`, `asegurar(ids)`), con todo producto que llegue
  de la API. El **carrito** (`cart.ts`, líneas que llaman `productsApi.porId(...)`) la necesita: hoy busca productos
  de forma síncrona en un almacén vacío. Deja `porId(id)` como lectura síncrona **desde la caché**, y en el carrito
  asegura la carga con `asegurar(ids)` antes de validar las líneas, igual que ya se hace con los eventos.
- Las funciones `porCategoria` y `masVendidos` dejan de existir: reemplaza a quienes las usan (A2 a A5).

**A2. `features/public/catalogo/catalogo.ts`: filtrado en el servidor.** Hoy carga todo y filtra, ordena y pagina
en el navegador (`filtrar`, `resultados`, `totalPaginas`). Cámbialo para que:
- la URL siga siendo la fuente de verdad. **Conserva los mismos query params de hoy**, para no romper enlaces ya
  compartidos, y tradúcelos al llamar a la API:

  | URL actual del front | Parámetro de la API |
  |---|---|
  | ruta `:categoria` | `categoria` |
  | `q` | `q` |
  | `stock=1` | `enStock=true` |
  | `min` / `max` | `precioMin` / `precioMax` |
  | `franquicia`, `linea`, `tipo`, `idioma`, `plataforma`, `marca` | el mismo nombre, repetido por cada valor |
  | `orden` | `sort` |
  | `pagina` (**base 1**) | `page` (**base 0**) = `pagina - 1` |
  | `porPagina` (20, 40, 60, 100) | `size` |

- los contadores y opciones de cada faceta salgan de `facets` de la respuesta (no se recalculan en el navegador);
- `totalElements` y `totalPages` alimenten el paginador y el texto "N productos";
- cada cambio de filtro haga **una** llamada. Usa `toObservable` + `switchMap` (o `rxResource`/`httpResource`) para
  cancelar la anterior, y agrega un `debounceTime` de unos 300 ms solo al texto de búsqueda;
- haya estado de **carga** (sin parpadeo del listado anterior) y de **error** con reintento;
- si `pagina` excede `totalPages`, se corrija a la última página, como hoy.

**A3. `features/public/home/home.ts`:** `masVendidos(4)` pasa a ser `destacados(4)`.

**A4. `features/public/producto-detalle/producto-detalle.ts`:**
- carga el producto con `obtener(id)` (hoy lo busca en `porCategoria('todos')`);
- los relacionados salen de `relacionados(id, 4)` (hoy se calculan en `computed`);
- un 404 `item-unavailable` muestra el estado "Producto no encontrado" que ya existe;
- la galería ya usa `imagenes` e `imagen`: la API manda la portada primero.

**A5. `app.routes.ts`, resolver `tituloProducto`:** hoy es síncrono con `porId`. Cámbialo a uno que devuelva un
`Observable<string>`: usa `obtener(id)`, y ante un error, `'Producto no encontrado | Primer Player'`. Aprovecha la
caché para no pedir el producto dos veces (resolver + página).

**A6. `features/admin/codigo-barras/codigo-barras.ts`: NO la toques.** Es la pantalla del mostrador (POS): arma la
venta con `PosSale`, que busca los productos en `ProductStore`. Se conectará cuando exista el módulo POS en el
backend. No registres productos de la API en `ProductStore` ni hagas puentes para que el POS local funcione. Déjala con
`// TODO(api): pendiente en backend — POS (venta en mostrador)`.

**A7. `ProductStore` (`core/services/product-store.ts`):** después de A1 a A5, solo debería quedar en uso por el POS
(`pos-sale.ts` y `codigo-barras.ts`), que sigue local. Déjalo con su `TODO(api)` apuntando al POS.

### Tarea B — Mis pedidos (cliente)

**B1.** Crea `core/services/pedidos-api.ts` con:

```ts
misPedidos(page = 0, size = 10): Observable<Pagina<Pedido>>
miPedido(codigo: string): Observable<Pedido>
```

**B2. `features/public/cuenta/perfil/perfil.ts`:** la lista "Mis pedidos" hoy mezcla compras simuladas
(`purchaseHistory.forUser`) con reservas locales de eventos. Cámbiala para que muestre **solo los pedidos de la API**:
- código, fecha (`createdAt` en formato `es-CL`), etiqueta y color del **estado** (tabla de 3.1), total e ítems
  (`name × quantity`);
- un **aviso destacado** cuando el estado sea `LISTO_RETIRO`: *"Tu pedido está listo. Retíralo en Av. Portales Ote.
  1126, San Bernardo, presentando el código PP-…"*;
- paginación con "Ver más" o un paginador simple;
- estado vacío: *"Aún no tienes pedidos."*

No muestres datos de `PurchaseHistory` en el perfil, aunque el carrito local siga escribiéndolos: la idea es no
mezclar datos simulados con datos reales.

**B3.** La lógica de **cancelar una reserva de evento** que hoy vive en el perfil (`purchaseHistory.removeItem`,
`eventReservations`) depende de inscripciones, y ese módulo **aún no existe en el backend**. Déjala como está, con un
`TODO(api): pendiente en backend — inscripciones (/me/inscripciones)`, pero sin mezclar sus datos en la lista de
pedidos. Si no se puede separar limpiamente, avísame.

### Tarea C — Panel de pedidos de la tienda (VENDEDOR/ADMIN)

**C1.** En `pedidos-api.ts` agrega:

```ts
buscarAdmin(filtro: { codigo?: string; estado?: string[]; canal?: 'web' | 'tienda';
                      desde?: string; hasta?: string; vendedor?: number; page?: number; size?: number })
  : Observable<Pagina<Pedido>>
cambiarEstado(codigo: string, estado: 'LISTO_RETIRO' | 'ENTREGADO'): Observable<Pedido>
```

**C2.** Crea una página nueva **`/admin/pedidos`** (componente standalone en `features/admin/pedidos/`), registrada
en `app.routes.ts` junto a las otras rutas de `admin` con el **mismo guard** que `stock` o `eventos`, y agrégala al
menú del panel con el mismo estilo que las demás entradas. Debe tener:
- un **buscador por código** destacado arriba, para cuando el cliente llega a retirar;
- filtros por estado (selección múltiple), canal y rango de fechas. Por defecto muestra
  `estado=PAGADO&estado=LISTO_RETIRO`: los pedidos por preparar o entregar;
- una tabla con código, fecha, cliente (`customerName` y `email`), canal, estado (etiqueta y color), total y ítems
  (resumidos, con detalle expandible);
- acciones según el estado, **solo** en pedidos `channel = 'web'` que tengan al menos un ítem `type = 'product'`:
  - `PAGADO` → botones **"Marcar listo para retiro"** y **"Entregar"**;
  - `LISTO_RETIRO` → botón **"Entregar"**;
  - en cualquier otro caso, sin acciones;
- **confirmación** antes de "Entregar" (es irreversible), mostrando código y cliente;
- después de cada acción, actualiza la fila con la respuesta (no recargues toda la lista). Ante un 409
  `order-invalid-state`, muestra el mensaje y recarga esa fila o la lista;
- paginación y estados de carga, vacío y error.

**C3.** **No** toques las páginas `admin/ventas` ni `admin/cierre-caja`. Dependen de pagos, caja y reportes, que no
existen en el backend (ver la sección 6).

### Tarea D — Errores

Agrega a `api-problem.ts` (traducción y prueba en `api-problem.spec.ts`):
- `order-invalid-state` → mensaje de la sección 3.5;
- verifica que `item-unavailable` tenga un mensaje adecuado para producto y para pedido. Si hoy dice "Producto no
  disponible", en pedidos muestra "El pedido no existe" desde el componente.

### Tarea E — README del frontend

Actualiza el `README.md` de **PrimerPlayerFront** con una sección **"Integración con la API"** que tenga:
1. cómo levantar el backend y el frontend juntos (proxy, `docker compose up -d`, `./mvnw spring-boot:run
   -Dspring-boot.run.profiles=dev`, `ng serve`);
2. una tabla de **qué está conectado a la API y qué sigue local**, con este formato:

   | Área | Estado | Servicio del front | Endpoint |
   |---|---|---|---|
   | Login y sesión | ✅ API | `auth.ts` | `/auth/**`, `/me` |
   | Catálogo público | ✅ API | `products-api.ts` | `/productos`, `/productos/destacados`, `/productos/{id}`, `/productos/{id}/relacionados` |
   | Mis pedidos | ✅ API | `pedidos-api.ts` | `/me/pedidos` |
   | Panel de pedidos (retiro) | ✅ API | `pedidos-api.ts` | `/admin/pedidos` |
   | Carrito y checkout | ⏳ local | `cart.ts` | pendiente en backend |
   | … | | | |

   Complétala con **todas** las áreas reales que encuentres en el inventario: eventos, cumpleaños, stock,
   inventario, POS, caja, ventas, usuarios, etc.;
3. las cuentas de prueba del perfil `dev`;
4. la lista de **códigos de error** (`code`) que el front traduce.

---

## 5. Verificación (muéstrame el resultado de cada punto)

Con el backend arriba y **la base vacía tal como está** (no cargues datos):

1. `ng build` sin errores y `ng test` en verde. Agrega pruebas para `products-api.ts` y `pedidos-api.ts` con
   `HttpTestingController`, simulando respuestas **con** datos: parámetros repetibles, `page` base 0, facetas,
   mapeo del pedido y manejo del 404 y del 409 `order-invalid-state`. Es la forma de probar la lógica sin datos reales.
2. **Catálogo:** `/catalogo/todos` llama a `GET /api/v1/productos` (verlo en la pestaña Network), recibe una página
   vacía y muestra el estado vacío. Cambiar un filtro genera **una** llamada con los parámetros correctos.
3. **Inicio:** llama a `/productos/destacados?limit=4` y no se rompe con la lista vacía.
4. **Detalle:** `/producto/999999` muestra "Producto no encontrado" (404) y el título de la pestaña lo refleja.
5. **Carrito:** `/carrito` sigue abriendo sin errores.
6. **Mis pedidos:** con `cliente@primerplayer.cl`, el perfil llama a `/me/pedidos` y muestra "Aún no tienes pedidos."
7. **Panel de pedidos:**
   - con `vendedor@primerplayer.cl`, `/admin/pedidos` carga y muestra el estado vacío;
   - buscar un código inexistente no rompe nada;
   - con `cliente@primerplayer.cl`, la ruta `/admin/pedidos` está bloqueada.
8. `grep` de `TODO(api)`: lista los que quedan y confirma que corresponden solo a lo pendiente de la sección 6.

Al final dame un resumen con: archivos creados o modificados, qué quedó conectado, qué sigue local, cualquier
diferencia que encontraste entre este documento y el OpenAPI, y decisiones que tomaste.

---

## 6. Qué NO hacer todavía (el backend aún no lo tiene)

| Área | Por qué esperar |
|---|---|
| Checkout y pago con Webpay | Son los próximos módulos del backend. **No** crees todavía la página `/pago/resultado`: cuando exista el pago, el backend redirigirá a `/pago/resultado?pedido=PP-…`, y esa página llegará con su propio prompt |
| Pagos dentro del pedido (`payments`) | El pedido aún no los trae |
| Inscripciones a eventos (`/me/inscripciones`) y cancelación | Módulo pendiente |
| POS, cierre de caja, ventas, reportes y estadísticas | Dependen de pagos y caja |
| Administración de usuarios, favoritos, alertas de stock, direcciones y perfil | Módulos pendientes |
| Despacho a domicilio | Decisión de negocio pendiente: por ahora **solo retiro en tienda** |
| Reembolsos | Decisión de negocio pendiente |

Si para cumplir una tarea crees que necesitas algo de esta lista, **detente y pregúntame**.
