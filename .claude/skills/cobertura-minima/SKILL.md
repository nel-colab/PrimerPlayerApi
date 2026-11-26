---
name: cobertura-minima
description: Regla de cobertura de pruebas de la API de Primer Player — todo código que llega a staging (develop) o producción (main) debe tener cobertura MÍNIMA de 80 % (líneas y ramas del código nuevo o modificado). Úsala al implementar o cambiar cualquier clase, al escribir pruebas, antes de hacer commit/push a develop o main, al abrir un PR y cuando el quality gate de SonarQube falle por cobertura.
argument-hint: "[clase, módulo o cambio a cubrir]"
---

# Cobertura mínima: 80 %

Alcance: `$ARGUMENTS`

## La regla

**Ningún código llega a staging (`develop`) ni a producción (`main`) con menos de 80 % de cobertura.**

- Se mide sobre el **código nuevo o modificado** (lo que SonarQube llama *New Code*): líneas **y** ramas
  (condiciones). Es la condición *Coverage on New Code ≥ 80 %* del quality gate, y Jenkins aborta el pipeline
  (`waitForQualityGate abortPipeline: true`) antes de construir la imagen: sin 80 % no hay despliegue.
- La cobertura **global** del proyecto nunca puede bajar con un cambio. Meta: ≥ 80 % global.
- No es negociable ni se "arregla después": si el cambio no llega a 80 %, no se sube. Una tarea no está
  terminada hasta que sus pruebas la cubren.
- La cobertura es el mínimo, no el objetivo: una prueba sin aserciones que solo "pasa por" el código no cuenta.
  Cada prueba verifica un comportamiento (resultado, excepción con su `code`, estado en la base, evento publicado).

## Control automático (no depende de nadie)

1. **JaCoCo `check`** (`pom.xml`, perfil `ci`, ejecución `cobertura-minima`): `./mvnw -Pci verify` — la etapa
   *Pruebas* de Jenkins — falla si la cobertura total baja de **80 % en líneas** o de **80 % en ramas**.
2. **Quality gate de SonarQube**: *Coverage on New Code* ≥ 80 % → Jenkins aborta antes de la imagen.

**El 80 % no se rebaja nunca** (decisión del usuario, 2026-11-26). Si un cambio deja la cobertura bajo 80 %, se
agregan pruebas; jamás se baja `<minimum>`. Jenkins mide ~1 punto menos que una máquina local (ramas que dependen
del entorno), así que localmente apunta a **≥ 81,5 % de ramas** antes de subir.

Si `verify` falla de forma rara (Flyway no encuentra `V1__*.sql`, pruebas que antes pasaban), suele ser el IDE
recompilando `target/` a la vez: corre `./mvnw -Pci clean verify`.

## Prohibido para inflar el número

- Agregar exclusiones (`sonar.coverage.exclusions`, `@Generated`, excludes de JaCoCo) para esconder código con
  lógica. Solo se excluye lo que no tiene lógica propia (clase `main`, código generado por MapStruct/annotation
  processors) y **siempre con aprobación explícita del usuario**.
- Pruebas sin aserciones, `assertTrue(true)`, o capturar y tragar excepciones en la prueba.
- `@Disabled` en pruebas que fallan para que el build pase.
- Bajar el umbral del quality gate en SonarQube o quitar `abortPipeline`.

## Cómo medir antes de subir

```bash
docker compose up -d                 # Testcontainers necesita Docker
./mvnw -B -Pci verify                # pruebas + reporte JaCoCo (perfil ci)
```

- Reporte: `target/site/jacoco/index.html` (por paquete y clase, con líneas rojas/amarillas sin cubrir).
- Resumen rápido del total (líneas y ramas) desde `target/site/jacoco/jacoco.csv`:

```bash
awk -F, 'NR>1{ml+=$8;cl+=$9;mb+=$6;cb+=$7} END{printf "Líneas %.1f%%  Ramas %.1f%%\n",100*cl/(ml+cl),100*cb/(mb+cb)}' target/site/jacoco/jacoco.csv
```

- Para el código nuevo, revisa en el HTML **cada clase que tocaste** (`git diff --name-only develop...HEAD`):
  cada una debe estar ≥ 80 % en líneas y ramas. Si una no llega, el cambio no llega.
- Tras el push, confirma en SonarQube (`runagard-api-develop` para develop, `runagard-api` para main) que el
  quality gate quedó en **Passed**. Si quedó en Failed, se corrige antes de cualquier otra cosa.

## Qué prueba cubre cada capa

| Capa | Tipo de prueba | Qué cubrir |
|---|---|---|
| `domain/` | Unitaria pura (JUnit 5 + AssertJ), sin Spring | Invariantes, cálculos de montos (`long`), transiciones de estado, cada rama de validación |
| `application/` | Unitaria con Mockito **o** integración `@SpringBootTest` + Testcontainers | Camino feliz y **cada** error de negocio (`out-of-stock`, `purchase-limit`, ...), eventos publicados, uso del `Clock` fijo |
| `infrastructure/` | Integración con Testcontainers (PostgreSQL real) | Consultas propias, `UPDATE ... WHERE stock >= :q` (0 filas = sin stock), restricciones de la base |
| `web/` | `@WebMvcTest` o MockMvc en integración | Códigos HTTP, `problem+json` con su `code`, validación de DTO, **matriz rol × endpoint** (401/403/200) |
| Listeners / jobs | Integración con Spring Modulith `Scenario` o llamada directa | Que reaccionan al evento y son idempotentes |

Las ramas que más se olvidan (y bajan la cobertura de ramas): `Optional` vacío, listas vacías, límites exactos
(stock = cantidad, cupo lleno), roles sin permiso, token vencido, errores de servicios externos (Webpay, correo,
S3) simulados con mocks.

## Flujo al terminar un cambio

1. Escribe o actualiza las pruebas junto con el código (no al final del sprint).
2. `./mvnw -B -Pci verify` en verde.
3. Revisa JaCoCo: cada clase tocada ≥ 80 % líneas y ramas; total no baja.
4. Si falta, agrega pruebas de los casos no cubiertos (mira las líneas rojas/amarillas), no exclusiones.
5. Recién entonces commit y push a `develop`. Después, quality gate **Passed** en SonarQube.
6. Al informar el trabajo, indica la cobertura obtenida (líneas y ramas de las clases tocadas y el total).
