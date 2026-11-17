---
name: migracion-flyway
description: Escribe una migración Flyway de PostgreSQL para Primer Player siguiendo las convenciones del esquema (montos BIGINT, timestamptz, CHECK de negocio, índices parciales, borrado lógico). Úsala cuando haya que crear o cambiar tablas, columnas, índices, restricciones o datos semilla.
argument-hint: "[descripción del cambio]"
---

# Migración Flyway

Cambio pedido: `$ARGUMENTS`

## Pasos

1. Lista `src/main/resources/db/migration/` y toma el siguiente número: `V<n+1>__<descripcion_en_snake_case>.sql`.
2. Lee las migraciones de las tablas que tocas para respetar nombres y restricciones existentes.
3. **Nunca edites una migración que ya existe** (Flyway falla por checksum en todos los ambientes ya migrados).
   Si algo está mal, se corrige con una migración nueva.
4. Escribe el SQL con las convenciones de abajo.
5. Comprueba que aplica desde cero: `./mvnw test -Dtest=MigracionesIT` (Testcontainers ejecuta todas las
   migraciones sobre un PostgreSQL vacío) o `docker compose up -d && ./mvnw flyway:migrate`.
6. Si agregaste una restricción de negocio, agrega una prueba de integración que demuestre que la base la rechaza.

## Convenciones

- Tablas y columnas en español, `snake_case`, singular.
- PK `BIGINT GENERATED ALWAYS AS IDENTITY` (o `UUID` para tokens y archivos).
- Montos `BIGINT` con `CHECK (x >= 0)` o `> 0`. Nunca `NUMERIC`, `REAL` ni `DOUBLE PRECISION`.
- Fechas `TIMESTAMPTZ NOT NULL DEFAULT now()` para `creado_en` y `actualizado_en`; `DATE` solo para días locales.
- Correos `CITEXT`. Enumeraciones como `VARCHAR` + `CHECK (col IN (...))`; **el largo debe alcanzar el valor más
  largo** (cuenta los caracteres).
- Entidades borrables con `eliminado_en TIMESTAMPTZ`; índices de lectura pública parciales
  `WHERE publicado AND eliminado_en IS NULL`.
- Toda FK con su índice si se consulta por ella.
- Comenta con `--` el porqué de cada índice no obvio.

## Cambios seguros en producción (expandir y contraer)

La revisión anterior del código debe seguir funcionando con el esquema nuevo (permite rollback):

| Cambio | Cómo |
| --- | --- |
| Agregar columna obligatoria | 1) agregar nullable o con `DEFAULT`; 2) rellenar; 3) en otra versión, `SET NOT NULL`. |
| Renombrar columna | Nueva columna + escritura doble + migrar datos; borrar la vieja en una versión posterior. |
| Borrar columna | Primero quitar su uso del código y desplegar; después la migración que la borra. |
| Índice en tabla grande | `CREATE INDEX CONCURRENTLY` en un archivo con `-- flyway:executeInTransaction=false` en la primera línea. |
| Nuevo valor en un CHECK de enumeración | `ALTER TABLE ... DROP CONSTRAINT ...; ADD CONSTRAINT ...` en la misma migración. |

## Datos

- Datos que todos los ambientes necesitan (categorías, franquicias, configuración) → migración versionada `V`.
- Datos de demostración → `src/main/resources/db/seed-dev/R__*.sql`, cargados solo en el perfil `dev`.
- Nunca usuarios con contraseñas reales ni datos personales en migraciones.
