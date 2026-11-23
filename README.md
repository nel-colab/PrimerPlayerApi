<div align="center">

# 🎮 Primer Player — API

**Backend de la tienda Primer Player** · San Bernardo, Chile
Cultura pop · TCG · Videojuegos · Consolas · Eventos y torneos

![Java](https://img.shields.io/badge/Java-25_LTS-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![Spring Modulith](https://img.shields.io/badge/Spring_Modulith-2.1-6DB33F?logo=spring&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-4169E1?logo=postgresql&logoColor=white)
![Flyway](https://img.shields.io/badge/Flyway-migraciones-CC0200?logo=flyway&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-compose-2496ED?logo=docker&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-wrapper-C71A36?logo=apachemaven&logoColor=white)

</div>

---

## 📖 Tabla de contenidos

- [Descripción](#-descripción)
- [Arquitectura](#-arquitectura)
- [Stack tecnológico](#-stack-tecnológico)
- [Requisitos previos](#-requisitos-previos)
- [Levantar el proyecto](#-levantar-el-proyecto)
- [Usuarios de prueba](#-usuarios-de-prueba)
- [Servicios locales](#-servicios-locales)
- [Documentación de la API](#-documentación-de-la-api)
- [Pruebas](#-pruebas)
- [Estructura del proyecto](#-estructura-del-proyecto)
- [Reglas de negocio clave](#-reglas-de-negocio-clave)
- [Estado del proyecto](#-estado-del-proyecto)
- [Solución de problemas](#-solución-de-problemas)

---

## 📝 Descripción

API REST que reemplaza la lógica que hoy vive en el frontend Angular de Primer Player y hace cumplir
**en el servidor** todas las reglas de negocio:

| Área | Qué cubre |
|---|---|
| 🛍️ **Catálogo** | Productos, categorías, franquicias, publicación |
| 📦 **Inventario** | Stock, ajustes y movimientos con descuento atómico |
| 🎟️ **Eventos** | Torneos, lanzamientos, cupos y solicitudes de cumpleaños |
| 🛒 **Ventas y checkout** | Carrito, pedidos y límite de compra por cliente |
| 💳 **Pagos** | Webpay Plus (Transbank) y pagos de mostrador |
| 🧾 **POS y caja** | Venta presencial, apertura y cierre de caja |
| 📊 **Reportes** | Estadísticas de ventas (solo ADMIN) |
| 🔐 **Identidad** | Registro, login, JWT + refresh token, roles |

---

## 🏛️ Arquitectura

**Monolito modular**: un solo despliegue y una sola base de datos, con fronteras de módulo verificadas por
[Spring Modulith](https://spring.io/projects/spring-modulith). Stock, pedido y pago ocurren en **una sola
transacción ACID**, por eso no se usan microservicios.

```
cl.primerplayer
├── identidad       ├── eventos         ├── caja
├── clientes        ├── ventas          ├── reportes
├── catalogo        ├── checkout        ├── notificaciones
├── inventario      ├── pagos           ├── auditoria
├── media           ├── pos             └── shared
```

Cada módulo sigue la misma estructura interna:

```
<modulo>/
├── api/             # Contrato público hacia otros módulos
├── events/          # Eventos de dominio publicados (outbox de Spring Modulith)
├── application/     # Casos de uso (@Transactional)
├── domain/          # Entidades y reglas de negocio
├── infrastructure/  # JPA, adaptadores externos
└── web/             # Controladores REST + DTO (records + MapStruct)
```

> Un módulo **solo** puede usar el paquete `api` (y `events`) de otro módulo. `ModularityTest` lo verifica en cada build.

---

## 🧰 Stack tecnológico

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 25 (LTS) |
| Framework | Spring Boot 4.1 · Spring Web MVC · Spring Data JPA (Hibernate) · Bean Validation |
| Seguridad | Spring Security + JWT propio (OAuth2 Resource Server) · Argon2id |
| Modularidad | Spring Modulith 2.1 (fronteras + outbox de eventos) |
| Base de datos | PostgreSQL 18 · Flyway |
| Mapeo | MapStruct · Lombok |
| Documentación | springdoc-openapi (Swagger UI) |
| Observabilidad | Spring Boot Actuator |
| Pagos | Webpay Plus — `transbank-sdk-java` |
| Infra local | Docker Compose: PostgreSQL, MinIO (S3), Mailpit (SMTP) |
| Pruebas | JUnit 5 · Testcontainers |

---

## ✅ Requisitos previos

| Herramienta | Versión | Verificar |
|---|---|---|
| ☕ JDK | 25 | `java -version` |
| 🐳 Docker Desktop | Reciente, **en ejecución** | `docker --version` |
| 🌿 Git | Cualquiera | `git --version` |

> No necesitas instalar Maven: el proyecto incluye el wrapper `./mvnw`.

---

## 🚀 Levantar el proyecto

### 1. Ir a la carpeta del proyecto

**Git Bash**
```bash
cd /e/proyectos/primer-player-api
```

**PowerShell / CMD**
```powershell
cd E:\proyectos\primer-player-api
```

> Si clonaste el repositorio en otra ruta, usa esa ruta: `git clone <url-del-repo> && cd primer-player-api`

### 2. Levantar los servicios de infraestructura

```bash
docker compose up -d
```

Esto inicia **PostgreSQL**, **MinIO**, **Mailpit** y **pgAdmin**. Verifica que estén arriba con:

```bash
docker compose ps
```

### 3. Ejecutar la API con el perfil `dev`

**Git Bash / Linux / macOS**
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**PowerShell**
```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Al arrancar, Flyway aplica las migraciones automáticamente y el perfil `dev` crea los usuarios de prueba.
La API queda disponible en **http://localhost:8080**.

### 4. Comprobar que funciona

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

### Resumen rápido (Git Bash)

```bash
cd /e/proyectos/primer-player-api
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Detener todo

```bash
# Ctrl + C para detener la API, luego:
docker compose down          # detiene los contenedores (conserva los datos)
docker compose down -v       # detiene y BORRA los volúmenes (base de datos limpia)
```

---

## 👤 Usuarios de prueba

Solo existen con el perfil `dev`. Contraseña para todos: **`PrimerPlayer2026!`**

| Rol | Email |
|---|---|
| 🛡️ ADMIN | `admin@primerplayer.cl` |
| 🧑‍💼 VENDEDOR | `vendedor@primerplayer.cl` |
| 🙋 CLIENTE | `cliente@primerplayer.cl` |

> La contraseña se puede cambiar con la propiedad `pp.dev.usuarios.password`.

---

## 🎲 Datos de demostración

En [`scripts/db/`](scripts/db/) hay dos scripts para partir de cero y ver la aplicación funcionando con datos realistas:

| Script | Qué hace |
|---|---|
| [`01-limpiar-base.sql`](scripts/db/01-limpiar-base.sql) | Deja **todas** las tablas vacías y reinicia IDs y códigos de pedido (no toca el esquema ni el historial de Flyway). |
| [`02-poblar-base.sql`](scripts/db/02-poblar-base.sql) | Llena todas las tablas con datos coherentes: 93 productos, 13 eventos, ~670 pedidos de 90 días, pagos, Webpay, cierres de caja, auditoría y más. |
| [`03-datos-base.sql`](scripts/db/03-datos-base.sql) | Repone solo lo necesario para operar (lo mismo que V6): categorías, franquicias, configuración de inventario y cliente de mostrador. **Sin productos ni ventas.** |

```bash
# Con Docker arriba, desde la raíz del proyecto (Git Bash):
./scripts/db/reiniciar-datos.sh                  # limpia y carga los datos de demostración
./scripts/db/reiniciar-datos.sh --entrega        # tienda vacía lista para cargar el stock real (01 + 03)
./scripts/db/reiniciar-datos.sh --solo-limpiar   # solo deja todo vacío (solo queda el admin)

# O cada script por separado:
docker compose exec -T postgres psql -U pp -d primerplayer -v ON_ERROR_STOP=1 < scripts/db/01-limpiar-base.sql
docker compose exec -T postgres psql -U pp -d primerplayer -v ON_ERROR_STOP=1 < scripts/db/02-poblar-base.sql
```

> ⚠️ **Solo para desarrollo local**: el script 01 borra todos los datos. Ambos se niegan a correr en una base que no sea `primerplayer`.
> También puedes pegarlos en el Query Tool de pgAdmin.

- Las fechas son **relativas a hoy**: los eventos siempre quedan en el futuro y el historial en el pasado.
- El script 02 corre en **una sola transacción** y al final valida la coherencia: stock = kardex, cupos = inscripciones,
  pagos = total, límites por cliente y que aparezcan todos los estados. Si algo no cuadra, no se guarda nada.
- Todas las cuentas usan la contraseña `PrimerPlayer2026!`. Además de las 3 de arriba:

| Cuenta | Para probar |
|---|---|
| `vendedora@demo.primerplayer.cl` | Segundo vendedor (ventas y cierres de caja a su nombre) |
| `camila.rojas@`, `valentina.perez@`, `diego.alarcon@` … `@demo.primerplayer.cl` | Clientes con pedidos, inscripciones y solicitudes |
| `joaquin.herrera@demo.primerplayer.cl` | Cuenta **bloqueada** (el login falla) |
| `antonia.vargas@demo.primerplayer.cl` | Cuenta **inactiva** |
| `matias.soto@demo.primerplayer.cl` | Correo **sin verificar** |

Casos listos para probar con `cliente@primerplayer.cl`: tiene un pedido **pendiente de pago** en curso, un pedido
**listo para retiro**, ya compró el **máximo de 2** Elite Trainer Box y llegó a **8/8** sobres Black Bolt (la próxima compra
responde `purchase-limit`), y tiene solicitudes de cumpleaños pendiente y aprobada. La **League Cup** está llena
(`event-full`) y la caja de **hoy** queda sin cerrar.

---

## 🐳 Servicios locales

| Servicio | URL / Puerto | Credenciales |
|---|---|---|
| 🚀 API | http://localhost:8080 | — |
| 🐘 PostgreSQL | `localhost:5433` · base `primerplayer` | `pp` / `pp` |
| 🔎 pgAdmin — visor web de la base | http://localhost:5050 | Sin login (modo escritorio) |
| 🪣 MinIO — API S3 | http://localhost:9000 | `ppminio` / `ppminio123` |
| 🪣 MinIO — consola | http://localhost:9001 | `ppminio` / `ppminio123` |
| 📬 Mailpit — bandeja web | http://localhost:8025 | — |
| 📬 Mailpit — SMTP | `localhost:1025` | — |

> PostgreSQL se expone en el puerto **5433** (no 5432) para no chocar con una instalación local.

### 🔎 Ver las tablas en el navegador (pgAdmin)

1. Abre **http://localhost:5050** (entra directo, sin usuario ni contraseña).
2. En el panel izquierdo: **Servers → Primer Player → Primer Player (local)** (la conexión ya viene registrada).
3. Navega a **Databases → primerplayer → Schemas → public → Tables**.
4. Clic derecho sobre una tabla → **View/Edit Data** para ver filas, o **Query Tool** para escribir SQL.

> La conexión se carga desde [`docker/pgadmin/servers.json`](docker/pgadmin/servers.json) solo la **primera vez** que se crea
> el contenedor. Si la borras por error: `docker compose rm -sf pgadmin && docker volume rm primer-player-api_pgadmindata && docker compose up -d`.

---

## 📚 Documentación de la API

Con la aplicación corriendo:

| Recurso | URL |
|---|---|
| 🧭 Swagger UI | http://localhost:8080/swagger-ui.html |
| 📄 OpenAPI (JSON) | http://localhost:8080/api/v1/openapi.json |
| ❤️ Health check | http://localhost:8080/actuator/health |

### Convenciones del contrato

- Ruta base: **`/api/v1`**
- `/admin/**` → requiere rol **VENDEDOR** o **ADMIN**
- Reportes, usuarios y cambios de límite → solo **ADMIN**
- `/me/**` → siempre filtra por el usuario del token
- Errores en formato **`application/problem+json`** (RFC 9457) con un campo `code` de negocio:

```json
{
  "type": "about:blank",
  "title": "Sin stock",
  "status": 409,
  "code": "out-of-stock",
  "detail": "No hay stock suficiente para el producto solicitado"
}
```

### Colecciones de Postman

En [`docs/postman/`](docs/postman/) hay colecciones listas para importar:

- `primer-player-identidad.postman_collection.json`
- `primer-player-catalogo-inventario.postman_collection.json`
- `primer-player-eventos.postman_collection.json`

---

## 🧪 Pruebas

> Requieren **Docker en ejecución** (Testcontainers levanta un PostgreSQL efímero).

```bash
./mvnw test      # pruebas unitarias + integración
./mvnw verify    # además: ModularityTest y snapshot de OpenAPI
```

| Tipo | Qué valida |
|---|---|
| Unitarias | Reglas de dominio y casos de uso con dobles de prueba |
| Integración | Endpoints reales contra PostgreSQL (Testcontainers) |
| Seguridad | Matriz rol × endpoint en cada controlador |
| Modularidad | Que ningún módulo acceda a los internos de otro |

---

## 🗂️ Estructura del proyecto

```
primer-player-api/
├── docker-compose.yml               # PostgreSQL, MinIO, Mailpit, pgAdmin
├── docker/
│   └── pgadmin/servers.json         # Conexión de pgAdmin pre-registrada
├── pom.xml
├── docs/
│   └── postman/                     # Colecciones de Postman
└── src/
    ├── main/
    │   ├── java/cl/primerplayer/    # Módulos de negocio + shared
    │   └── resources/
    │       ├── application.yaml     # Configuración base
    │       ├── application-dev.yaml # Configuración de desarrollo local
    │       └── db/
    │           ├── migration/       # Migraciones Flyway (fuente de verdad del esquema)
    │           └── seed-dev/        # Datos de demostración (solo perfil dev)
    └── test/java/cl/primerplayer/   # Pruebas unitarias e integración
```

### Migraciones de base de datos

| Versión | Contenido |
|---|---|
| V1 | Identidad (usuarios, sesiones) |
| V2 | Catálogo |
| V3 | Eventos y ventas |
| V4 | Pagos |
| V5 | Caja y auditoría |
| V6 | Semilla base |
| V7 | Eventos de cumpleaños y lanzamientos |
| V8 | Outbox de eventos de Spring Modulith (`event_publication`) |
| V9 | Administración de usuarios (baneo, lista negra, cambio de contraseña pendiente, último acceso) |

> Todo cambio de esquema es una **migración nueva**. Nunca se edita una migración ya aplicada.

---

## ⚖️ Reglas de negocio clave

- 💰 **Montos** en pesos chilenos (CLP) como `long` / `BIGINT`. Nunca decimales.
- 🕐 **Fechas** en UTC (`Instant` / `timestamptz`). Los cortes diarios se calculan en `America/Santiago`.
- 📦 **Stock y cupos** se descuentan con `UPDATE ... WHERE stock >= :q` atómico, con `CHECK (stock >= 0)` como red final.
- 🌐 Las transacciones **no** incluyen llamadas HTTP externas (Webpay, correo, S3).
- 🔒 Los logs nunca contienen contraseñas, tokens, cookies ni datos de tarjeta.

---

## 📈 Estado del proyecto

| Módulo | Estado |
|---|---|
| `identidad` | ✅ Registro, login, refresh, logout, verificación de correo, recuperación y cambio de contraseña, perfil, administración de usuarios (roles, baneo, lista negra) |
| `catalogo` | ✅ Catálogo público (búsqueda con facetas, destacados, detalle, relacionados) y administración de productos |
| `inventario` | ✅ Ajustes de stock y movimientos |
| `eventos` | ✅ Eventos públicos, administración, solicitudes de cumpleaños, inscripciones (mis inscripciones, cancelación e inscritos) |
| `ventas` | ✅ Pedidos web (reserva atómica de stock y cupos, límite por cliente, idempotencia), mis pedidos y panel con retiro en tienda |
| `checkout` | ✅ Validación del carrito, checkout y retorno de Webpay; vence reservas sin pago cada minuto (ShedLock) |
| `pagos` | ✅ Webpay Plus con `transbank-sdk-java` 6.2.1 (ambiente de integración; producción por variables de entorno) |
| `pos` | ✅ Lectura por código de barras (la pistola es un teclado: GET común), venta de mostrador con pago mixto y anulación |
| `caja` | ✅ Resumen del día con los pagos reales, cierre con historial de versiones y listado de cierres |
| `media` | ✅ Subida de imágenes (MinIO, bucket público) y comprobantes de transferencia (bucket privado, URL firmada 5 min) |
| `reportes` | ✅ Ventas del día con variación, por hora, por día del mes y por mes; métodos de pago y top 5 (solo ADMIN) |
| `clientes` | ✅ Direcciones, favoritos y alertas de stock (aviso por correo al reponerse) |
| `notificaciones` | ✅ Correos de verificación, recuperación, pedido confirmado y alerta de stock (Mailpit en local) |
| `auditoria` | ✅ Registro por eventos (acciones sobre usuarios) y consulta `GET /admin/auditoria` (solo ADMIN) |

> **Decisiones de negocio vigentes:** por ahora solo **retiro en tienda** (el despacho se definirá después), para
> comprar hay que **iniciar sesión con el correo verificado**, los **reembolsos** quedan para más adelante y el stock
> de un pago en curso se reserva **15 minutos**.

> 📊 Qué pantalla del frontend ya usa la API y cuál sigue local: [`docs/estado-integracion.md`](docs/estado-integracion.md)
> (se actualiza al cerrar cada tarea).

### Webpay Plus

| Variable de entorno | Desarrollo (por defecto) | Producción |
|---|---|---|
| `PP_WEBPAY_AMBIENTE` | `integracion` | `produccion` |
| `PP_WEBPAY_CODIGO_COMERCIO` / `PP_WEBPAY_API_KEY` | vacías = credenciales públicas de prueba del SDK | las que entrega Transbank tras la validación |
| `PP_WEBPAY_URL_FORMULARIO` | `https://webpay3gint.transbank.cl/webpayserver/initTransaction` | URL del formulario de producción (confirmarla con Transbank) |
| `PP_CHECKOUT_URL_RETORNO` | `http://localhost:4200/api/v1/pagos/webpay/retorno` | URL pública del backend + `/api/v1/pagos/webpay/retorno` |
| `PP_CHECKOUT_URL_RESULTADO` | `http://localhost:4200/pago/resultado` | URL pública del frontend + `/pago/resultado` |

Tarjetas de prueba: ver la documentación de Webpay Plus en `transbankdevelopers.cl`.

### Inicio de sesión con Google

| Variable de entorno | Desarrollo (por defecto) | Producción |
|---|---|---|
| `PP_AUTH_GOOGLE_CLIENT_ID` | vacía = botón de Google sin función | ID de cliente OAuth "Aplicación web" de Google Cloud, con el dominio del frontend en "Orígenes autorizados de JavaScript" |

Solo la lee el backend: el frontend la obtiene de `GET /api/v1/auth/google/config`. No es un secreto (Google la
muestra en cada inicio de sesión), así que no se necesita el *client secret*.

### Endpoints disponibles

| Área | Endpoints | Acceso |
|---|---|---|
| Autenticación | `POST /auth/registro`, `/auth/login`, `/auth/refresh`, `/auth/logout` · `GET /me` | Público / sesión |
| Cuenta | `POST /auth/verificacion`, `/auth/recuperacion`, `/auth/restablecimiento` · `GET`/`PUT /me/perfil` · `PUT /me/password` · `POST /me/verificacion` | Público / sesión |
| Cliente | CRUD `/me/direcciones` · `GET /me/favoritos`, `PUT`/`DELETE /me/favoritos/{productoId}` · `GET /me/alertas-stock`, `PUT`/`DELETE /me/alertas-stock/{productoId}` | CLIENTE |
| Catálogo | `GET /categorias`, `/franquicias`, `/productos`, `/productos/destacados`, `/productos/{id}`, `/productos/{id}/relacionados` | Público |
| Eventos | `GET /eventos`, `/eventos/{id}`, `/agenda/ocupados` | Público |
| Cumpleaños (cliente) | `GET`/`POST /me/solicitudes-cumpleanos` · `POST /me/solicitudes-cumpleanos/{id}/cancelacion` | CLIENTE |
| Pedidos (cliente) | `GET /me/pedidos`, `/me/pedidos/{codigo}` | CLIENTE |
| Inscripciones (cliente) | `GET /me/inscripciones` · `POST /me/inscripciones/{id}/cancelacion` | CLIENTE |
| Carrito | `POST /carrito/validacion` | Público (con sesión evalúa el límite por cliente) |
| Checkout | `POST /checkout` (encabezado `Idempotency-Key`) | CLIENTE con correo verificado |
| Webpay | `GET`/`POST /pagos/webpay/retorno` (lo llama el navegador al volver de Transbank) | Público |
| Productos (admin) | `GET`/`POST /admin/productos` · `GET`/`PUT`/`DELETE /admin/productos/{id}` · `PATCH /admin/productos/{id}/publicacion` · `GET /admin/productos/indicadores` | VENDEDOR, ADMIN |
| Importación/exportación de stock | `GET /admin/productos/exportar` (JSON o XLSX) · `POST /admin/productos/importar` (JSON, XLSX o CSV) | ADMIN |
| Inventario (admin) | `POST /admin/inventario/{id}/ajustes` · `GET /admin/inventario/{id}/movimientos` · `GET`/`PUT /admin/inventario/configuracion` | VENDEDOR, ADMIN |
| Eventos (admin) | CRUD `/admin/eventos` · `PATCH /admin/eventos/{id}/publicacion` · `GET /admin/eventos/{id}/inscritos` | VENDEDOR, ADMIN |
| Cumpleaños (admin) | `GET /admin/solicitudes-cumpleanos` · `POST …/{id}/aprobacion`, `…/{id}/rechazo` | VENDEDOR, ADMIN |
| Pedidos (admin) | `GET /admin/pedidos` · `PATCH /admin/pedidos/{codigo}/estado` | VENDEDOR, ADMIN |
| POS (mostrador) | `GET /admin/pos/productos/codigo/{codigo}` · `POST /admin/pos/validacion` · `POST /admin/pos/ventas` (encabezado `Idempotency-Key`) | VENDEDOR, ADMIN |
| POS (anulación) | `POST /admin/pos/ventas/{codigo}/anulacion` | ADMIN |
| Caja | `GET /admin/caja/{fecha}` · `PUT /admin/caja/{fecha}/cierre` · `GET /admin/caja/cierres` | VENDEDOR, ADMIN |
| Archivos | `POST /admin/media/imagenes`, `/admin/media/comprobantes` (multipart `archivo`) · `GET /admin/media/comprobantes/{id}` | VENDEDOR, ADMIN |
| Reportes | `GET /admin/reportes/dia`, `/por-hora`, `/por-dia`, `/por-mes` (JSON); `/por-dia` y `/por-mes` también exportan XLSX con `format=excel` o `Accept` de Excel | ADMIN |
| Auditoría | `GET /admin/auditoria` | ADMIN |
| Usuarios | `GET /admin/usuarios`, `/admin/usuarios/indicadores` · `PATCH …/{id}/rol` · `POST`/`DELETE …/{id}/baneo`, `…/{id}/lista-negra` · `POST …/{id}/restablecimiento-password`, `…/{id}/verificacion`, `…/{id}/cierre-sesiones` | ADMIN |

Todos bajo `/api/v1`. El detalle de parámetros y respuestas está en Swagger UI.

`GET /admin/productos/exportar` devuelve el array JSON completo o un XLSX si se usa `?format=excel` o el `Accept`
de Excel. `POST /admin/productos/importar` acepta `{ "filas": [...] }` o multipart con la parte `archivo` (`.xlsx`
o `.csv`). Cada fila se procesa de forma independiente; `precio` es CLP entero. Para crear un producto nuevo se
requiere `categoria` con el slug de una categoría hoja. En actualizaciones, los campos de texto opcionales vacíos
conservan el valor actual; `precio` y `stock` vacíos se interpretan como cero, `publicado` vacío como `false`.
Importar reemplaza el stock con una operación atómica y registra la diferencia en el kardex con motivo
`Importación Excel`.

`GET /admin/reportes/por-dia` acepta el filtro existente `mes=AAAA-MM` o `year` + `month`; `/por-mes` acepta `anio`
o `year`. Ambos devuelven JSON por defecto y generan XLSX con `format=excel` o el `Accept` de Excel.

`GET /admin/pedidos` incluye `phone` y `rut` del perfil del cliente (`null` si no están informados o si el canal es
`tienda`). `PATCH /admin/pedidos/{codigo}/estado` acepta `{ "estado": "LISTO_RETIRO" | "ENTREGADO",
"notificarCliente": true | false }`. `notificarCliente: true` envía un correo con el detalle del pedido al marcarlo
`LISTO_RETIRO`; se ignora al marcar `ENTREGADO`. Ambos estados se permiten para pedidos web de productos, eventos o
mixtos. Las ventas de mostrador no admiten esta transición.

### Prompts para el frontend

Instrucciones listas para entregar al agente que trabaja en `PrimerPlayerFront`:

| Documento | Qué integra |
|---|---|
| [`docs/prompt-frontend.md`](docs/prompt-frontend.md) | Primera conexión: login, eventos, cumpleaños, mantenedor de productos e inventario |
| [`docs/prompt-frontend-catalogo-pedidos.md`](docs/prompt-frontend-catalogo-pedidos.md) | Catálogo público, mis pedidos y panel de pedidos con retiro en tienda |
| [`docs/prompt-frontend-checkout-inscripciones.md`](docs/prompt-frontend-checkout-inscripciones.md) | Carrito, checkout con Webpay, página de resultado del pago, compra de cupos, mis inscripciones e inscritos |
| [`docs/prompt-frontend-revision-integracion.md`](docs/prompt-frontend-revision-integracion.md) | Solo diagnóstico: lista todos los servicios del backend y pide al front una tabla de qué falta por conectar |

---

# abrir docker desktop
# 1. Ir a la carpeta
# cd /e/proyectos/primer-player-api
# 2. Levantar PostgreSQL, MinIO, Mailpit y pgAdmin
# docker compose up -d
# 3. Levantar la API
# ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev