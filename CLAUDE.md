# Primer Player — API (backend)

Backend de la tienda Primer Player (San Bernardo, Chile): cultura pop, TCG, videojuegos, consolas, eventos y torneos.
Reemplaza la lógica que hoy vive en el frontend Angular 22 (catálogo, stock, carrito, pedidos, eventos, POS, caja,
estadísticas, autenticación) y hace cumplir **en el servidor** todas las reglas de negocio.

La arquitectura completa está en `docs/arquitectura.md` (exportada del documento de arquitectura).
Léela cuando necesites el detalle de una sección (ADR, contrato de API, flujos, seguridad, despliegue).
El esquema de base de datos ya existe en `src/main/resources/db/migration/` (V1–V6, probado en PostgreSQL):
es la fuente de verdad del modelo de datos.

## Stack

- Java 25 (LTS), Spring Boot 4.x, Maven.
- Spring Web, Spring Data JPA (Hibernate), Bean Validation, Spring Security (JWT propio), Actuator, Flyway,
  springdoc-openapi, **Spring Modulith** (fronteras de módulos + outbox de eventos), MapStruct, ShedLock, Bucket4j.
- PostgreSQL 18. Testcontainers para pruebas. MinIO y Mailpit en local (docker compose).
- Webpay Plus con el SDK oficial `transbank-sdk-java`.

## Arquitectura en una línea

**Monolito modular**: un solo despliegue, una sola base, 14 módulos de negocio + `shared` bajo `cl.primerplayer`.
No proponer microservicios: stock, pedido y pago deben ocurrir en una sola transacción ACID.

Módulos: `identidad`, `clientes`, `catalogo`, `inventario`, `media`, `eventos`, `ventas`, `checkout`, `pagos`,
`pos`, `caja`, `reportes`, `notificaciones`, `auditoria` (+ `shared`).

## Comandos

```bash
docker compose up -d            # postgres, minio, mailpit
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
./mvnw test                     # unitarias + integración (Testcontainers, requiere Docker)
./mvnw verify                   # incluye ModularityTest y snapshot de OpenAPI
```

Producción (runagard.cl, VPS con Docker, Jenkins + SonarQube + Grafana): ver `deploy/README.md`.
Perfil `prod` en `application-prod.yaml`; Actuator escucha en el 8081 (solo red interna).

## Reglas que nunca se rompen

1. **Montos** en CLP como `long` / `BIGINT`, nunca `double`, `float` ni `BigDecimal` con decimales.
2. **Fechas** como `Instant` / `timestamptz` (UTC). Los cortes diarios se calculan en `ZoneId.of("America/Santiago")`.
   Usa el `Clock` inyectable de `shared`, nunca `Instant.now()` directo (las pruebas lo fijan).
3. **Stock y cupos** se descuentan con `UPDATE ... WHERE stock >= :q` atómico; 0 filas = sin stock.
   Nunca leer, restar y guardar en Java. El `CHECK (stock >= 0)` de la base es la red final.
4. Las transacciones **no** incluyen llamadas HTTP externas (Webpay, correo, S3).
5. Un módulo solo usa el paquete `api` (y `events`) de otro módulo. Nunca sus entidades, repositorios ni tablas.
6. Las entidades JPA no salen del módulo: la API REST expone `record` DTO mapeados con MapStruct.
7. Errores como `application/problem+json` (RFC 9457) con un campo `code` de negocio en kebab-case
   (`out-of-stock`, `purchase-limit`, ...). El frontend depende de esos códigos.
8. Todo cambio de esquema es una migración Flyway nueva; nunca editar una migración ya aplicada.
9. Ruta base `/api/v1`. `/admin/**` exige VENDEDOR o ADMIN; reportes, usuarios y cambios de límite exigen ADMIN;
   `/me/**` filtra siempre por el usuario del token.
10. Logs sin contraseñas, tokens, cookies ni datos de tarjeta.

## Convenciones de código

- Nombres de dominio en español (`Pedido`, `descontarStock`), términos técnicos en inglés (`Repository`, `Controller`).
- Estructura de cada módulo: `api/`, `events/`, `application/`, `domain/`, `infrastructure/`, `web/`.
- `@Transactional` en servicios de `application`, nunca en controladores.
- Cada endpoint nuevo lleva prueba de seguridad (matriz rol × endpoint) y aparece en el OpenAPI.

## Skills del proyecto

- `/primer-player-dominio` — reglas de negocio, modelo de datos y contrato de API.
- `/nuevo-modulo-spring` — crear o completar un módulo con la estructura estándar.
- `/migracion-flyway` — escribir una migración de base de datos.
- `/cobertura-minima` — todo código que va a staging o producción debe tener cobertura mínima de 80 %.
- `/iniciar-proyecto` — generar el esqueleto del proyecto (solo una vez; solo se ejecuta si la invocas).
