---
name: iniciar-proyecto
description: Genera el esqueleto del proyecto Spring Boot de Primer Player (pom.xml, aplicación, módulo shared, configuración por perfiles, prueba de modularidad). Ejecutar una sola vez al comenzar.
disable-model-invocation: true
---

# Iniciar el proyecto

Solo se ejecuta cuando el usuario escribe `/iniciar-proyecto`. Si ya existe `pom.xml`, detente y avisa.

## 1. Verificar versiones vigentes

Antes de fijar versiones, consulta las últimas estables (no uses versiones de memoria):
Spring Boot 4.x, Spring Modulith compatible con esa versión de Boot, springdoc-openapi compatible con Boot 4,
MapStruct, ShedLock, Bucket4j, Testcontainers, `transbank-sdk-java`.
Si no puedes consultarlas, usa `start.spring.io` como referencia y deja un comentario `<!-- verificar versión -->`.

## 2. `pom.xml` (Maven, Java 25)

- Parent `spring-boot-starter-parent` 4.x; `groupId` `cl.primerplayer`, `artifactId` `primer-player-api`.
- Starters: `web`, `data-jpa`, `validation`, `security`, `oauth2-resource-server` (validación de JWT con Nimbus),
  `actuator`, `mail`, `thymeleaf` (plantillas de correo).
- `flyway-core` + `flyway-database-postgresql`, driver `postgresql`.
- `spring-modulith-starter-core`, `spring-modulith-starter-jpa` (registro de eventos / outbox),
  `spring-modulith-starter-test` (test).
- `springdoc-openapi-starter-webmvc-ui`.
- `mapstruct` + `mapstruct-processor` en `annotationProcessorPaths` (junto a Lombok si se usa, Lombok primero).
- `shedlock-spring` + `shedlock-provider-jdbc-template`, `bucket4j` (rate limiting), `software.amazon.awssdk:s3`,
  `org.apache.tika:tika-core`, `com.github.transbankdevelopers:transbank-sdk-java`.
- Test: `spring-boot-starter-test`, `spring-security-test`, `testcontainers` (`postgresql`, `junit-jupiter`),
  `wiremock`.
- Plugin `maven-failsafe-plugin` para las pruebas `*IT`.
- Agrega el Maven Wrapper (`./mvnw`).

## 3. Estructura inicial

```text
src/main/java/cl/primerplayer/
├── PrimerPlayerApplication.java     # @SpringBootApplication @EnableScheduling
└── shared/
    ├── api/ExcepcionNegocio.java    # ver plantillas de la skill nuevo-modulo-spring
    ├── web/ManejadorErrores.java    # @RestControllerAdvice → problem+json con "code"
    ├── web/Pagina.java              # record de paginación {content, page, size, totalElements, totalPages}
    ├── web/RequestIdFilter.java     # genera requestId, lo pone en MDC y en la cabecera X-Request-Id
    ├── tiempo/RelojConfig.java      # @Bean Clock (UTC) + ZONA = ZoneId.of("America/Santiago")
    └── seguridad/SeguridadConfig.java  # SecurityFilterChain base: stateless, CORS, reglas por ruta
src/main/resources/
├── application.yml                  # comunes
├── application-dev.yml              # local: docker compose, Flyway + db/seed-dev, logs legibles
├── application-staging.yml
├── application-prod.yml             # logs JSON (ECS), sin seed, secretos por variables de entorno
└── db/migration/                    # YA EXISTE: V1–V6, no tocar
src/test/java/cl/primerplayer/
├── ModularityTest.java              # ApplicationModules.of(PrimerPlayerApplication.class).verify()
└── MigracionesIT.java               # Testcontainers postgres:18 + Flyway desde cero
```

Crea un paquete vacío con `package-info.java` (`@ApplicationModule`) para cada uno de los 14 módulos listados en
`CLAUDE.md`, con sus `allowedDependencies` según la tabla de la skill `nuevo-modulo-spring`.

## 4. Configuración clave

- `spring.jpa.hibernate.ddl-auto=validate` (el esquema lo maneja solo Flyway). `spring.jpa.open-in-view=false`.
- `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`.
- `spring.flyway.locations=classpath:db/migration` (+ `classpath:db/seed-dev` solo en `dev`).
- `spring.modulith.events.jdbc.schema-initialization.enabled=true` (crea la tabla del outbox) y
  `spring.modulith.events.republish-outstanding-events-on-restart=true`.
- `server.servlet.context-path` vacío; los controladores usan `/api/v1/...`.
- Propiedades propias con prefijo `pp.`: `pp.checkout.reserva-minutos=15`, `pp.cors.origenes`,
  `pp.jwt.*`, `pp.webpay.*`, `pp.almacenamiento.*`.
- Secretos solo por variables de entorno: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_PRIVATE_KEY`,
  `WEBPAY_COMMERCE_CODE`, `WEBPAY_API_KEY`, `S3_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `SMTP_*`.
- `dev`: Webpay con las credenciales públicas del ambiente de integración de Transbank.

## 5. Verificar

```bash
docker compose up -d
./mvnw verify
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
curl -s localhost:8080/actuator/health
```

Termina mostrando el árbol creado y el resultado de `./mvnw verify`.
