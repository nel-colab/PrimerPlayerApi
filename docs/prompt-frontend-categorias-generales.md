# Prompt: categorías generales (TCG, Figuras, Videojuegos, Consolas, Accesorios)

> Copia el contenido de este documento y entrégalo al agente que trabaja en `PrimerPlayerFront`.
> Backend de referencia: `PrimerPlayerApi` (Spring Boot 4). Frontend: Angular 22.

---

## Contexto y objetivo

Trabaja en el repositorio **PrimerPlayerFront** (`E:\proyectos\PrimerPlayerFront`). La API está en
`E:\proyectos\PrimerPlayerApi` y ya aplica este cambio (migración V13). El contrato vigente está en
`GET /api/v1/openapi.json`.

Las subcategorías desaparecieron: "TCG Pokémon", "Figuras · Naruto", "PS5", "Controles", "Consolas usadas" y
similares. Ahora hay **5 categorías generales** y la subdivisión va en campos del producto que el catálogo ya filtra
como facetas.

| Categoría (`slug`) | Título | Se subdivide por | Valores en uso |
| --- | --- | --- | --- |
| `tcg` | TCG | `franquicia` | Pokémon, One Piece, Mitos y Leyendas, Yu-Gi-Oh! |
| `figuras` | Figuras | `franquicia` (y `marca`) | Naruto, One Piece, Pokémon, Dragon Ball... Funko Pop es **marca** `Funko Pop`, no franquicia |
| `venta-videojuegos` | Videojuegos | `plataforma` | PlayStation 5, PlayStation 4, Xbox Series X\|S, Xbox One, Nintendo Switch, Nintendo Switch 2, Retro |
| `consolas` | Consolas | `tipo` | Venta, Usadas, Arriendo |
| `accesorios-consolas` | Accesorios | `tipo` (y `plataforma`) | Controles, Audífonos, Fundas y protección, Cargadores y cables |

- `todos` sigue existiendo como agrupadora de navegación (sin filtro).
- Los productos existentes ya se migraron: cada uno quedó en su categoría general con su franquicia, plataforma o
  tipo.
- Los slugs viejos (`tcg-pokemon`, `figuras-naruto`, `juegos-ps5`, `venta-consolas`...) **ya no existen**: filtrar por
  ellos devuelve una lista vacía.

No hagas cambios en el backend. Si algo no coincide con el OpenAPI, informa la diferencia antes de inventar.

## Contrato

- `GET /api/v1/categorias` devuelve las 6 entradas (`todos` + las 5 generales), ordenadas, todas sin `hijos`.
- `GET /api/v1/productos?categoria=tcg&franquicia=Pokémon` (y `plataforma=`, `tipo=`, `marca=`, repetibles). Las
  `facets` de la respuesta traen los valores disponibles con su conteo. Así se arma "TCG Pokémon" o
  "Juegos de PS5".
- `POST/PUT /admin/productos`: `categoria` debe ser una de las 5 generales (`todos` → 400 `validation`). `tipo`
  (máx. 40), `plataforma` (máx. 30), `marca` (máx. 60) y `franquicia` (una existente de `GET /franquicias`) son
  opcionales.

## Tareas

### 1. Enlaces y textos con slugs viejos

- `src/app/app.html` (footer "Productos"): TCG → `/catalogo/tcg`, y agrega Consolas (`/catalogo/consolas`) y
  Accesorios (`/catalogo/accesorios-consolas`) si cabe.
- `src/app/features/public/home/home.ts` (`categorias`): usa las categorías generales. Si quieres mantener un acceso
  directo a "TCG Pokémon", enlaza a `/catalogo/tcg?franquicia=Pokémon`.
- `src/app/features/public/eventos/eventos.html:59`: `/catalogo/tcg-pokemon` → `/catalogo/tcg`.
- `src/app/core/services/products-api.ts`: el mapa de etiquetas por slug. Deja solo los 5 slugs generales y `todos`;
  mejor aún, toma los títulos de `GET /categorias` en vez de un mapa fijo.
- Actualiza fixtures y specs (`producto-fixtures.ts`, `products-api.spec.ts`, `admin-stock-api.spec.ts`,
  `stock-form.spec.ts`) con los slugs nuevos.

### 2. Catálogo público (`/catalogo/:categoria`)

- La subnavegación de cada categoría sale de las facetas:
  - TCG y Figuras: chips o lista de **franquicia**.
  - Videojuegos: **plataforma**.
  - Consolas: **tipo** (Venta / Usadas / Arriendo).
  - Accesorios: **tipo**.
- Lee los filtros de los query params (`?franquicia=Pokémon`) para que los enlaces del punto 1 funcionen y se puedan
  compartir.
- **Compatibilidad:** si la ruta trae un slug viejo, redirige al nuevo con su filtro, para no romper enlaces
  guardados o compartidos:

| Slug viejo | Redirige a |
| --- | --- |
| `tcg-pokemon` · `tcg-one-piece` · `tcg-mitos-y-leyendas` · `tcg-yu-gi-oh` | `tcg?franquicia=` Pokémon · One Piece · Mitos y Leyendas · Yu-Gi-Oh! |
| `figuras-<franquicia>` | `figuras?franquicia=<nombre>` |
| `figuras-funko-pop` | `figuras?marca=Funko Pop` |
| `figuras-otras` | `figuras` |
| `juegos-ps5` · `ps4` · `xbox-series` · `xbox-one` · `switch` · `switch-2` · `retro` | `venta-videojuegos?plataforma=` PlayStation 5 · PlayStation 4 · Xbox Series X\|S · Xbox One · Nintendo Switch · Nintendo Switch 2 · Retro |
| `venta-consolas` · `consolas-usadas` · `arriendo-consolas` | `consolas?tipo=` Venta · Usadas · Arriendo |
| `accesorios-controles` · `audifonos` · `fundas` · `cargadores` | `accesorios-consolas?tipo=` Controles · Audífonos · Fundas y protección · Cargadores y cables |

### 3. Formulario de productos (`features/admin/stock-form`)

- El selector de categoría ya se llena desde `GET /categorias` (`categoriasHoja`): mostrará solo las 5. Verifica que
  no quede una opción vacía o duplicada.
- Hoy `aRequest` reenvía `tipo` y `plataforma` del producto sin dejar editarlos. Agrega campos según la categoría
  elegida:
  - **TCG / Figuras:** se mantiene "Franquicia / Anime". En Figuras agrega **Marca** (sugiere "Funko Pop").
  - **Videojuegos:** **Plataforma**, un select con los valores de la tabla más "Otra" con texto libre (máx. 30).
  - **Consolas:** **Tipo**, un select obligatorio en el formulario con Venta / Usadas / Arriendo. Más **Plataforma**.
  - **Accesorios:** **Tipo**, un select con los valores de la tabla más "Otro" (máx. 40). Más **Plataforma**
    (incluye "Multiplataforma").
  - Al cambiar de categoría, limpia los campos que no aplican a la nueva (por ejemplo, la franquicia al pasar de
    Figuras a Consolas).
- El listado del mantenedor (`features/admin/stock`) puede mostrar la subdivisión junto a la categoría (por ejemplo,
  "TCG · Pokémon" o "Consolas · Usadas").

### 4. Importación Excel

- La columna `categoria` ahora usa los slugs generales. Actualiza la plantilla o ejemplo descargable si existe, y el
  texto de ayuda de la pantalla de importación.

## Pruebas

- Los enlaces del footer, el home y eventos apuntan a slugs que existen.
- La redirección de slugs viejos: `/catalogo/tcg-pokemon` → `/catalogo/tcg?franquicia=Pokémon`, y al menos un caso
  de plataforma y uno de tipo.
- El catálogo con `?franquicia=` envía el filtro a la API y marca la faceta como activa.
- `stock-form`: los campos por categoría aparecen y se limpian al cambiar de categoría. El request lleva `tipo`,
  `plataforma` y `marca`. Consolas sin tipo no se envía.

Al terminar: `ng build` sin errores y `ng test --watch=false` en verde. Resume qué archivos cambiaste y qué
quedó pendiente.
