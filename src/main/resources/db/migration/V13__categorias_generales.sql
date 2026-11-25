-- Categorías generales: TCG, Figuras, Videojuegos, Consolas y Accesorios. Las subcategorías (TCG Pokémon,
-- Figuras · Naruto, PS5, Controles, Consolas usadas...) desaparecen: la subdivisión pasa a los campos del producto,
-- que el catálogo ya filtra como facetas:
--   TCG y Figuras    → franquicia (Pokémon, One Piece, Naruto...)
--   Videojuegos      → plataforma (PlayStation 5, Nintendo Switch...)
--   Consolas         → tipo (Venta, Usadas, Arriendo)
--   Accesorios       → tipo (Controles, Audífonos...)
--   Funko Pop        → Figuras con marca 'Funko Pop' (no es una franquicia)
--
-- Los productos existentes se mueven a su categoría general. Los campos solo se completan si estaban vacíos: un valor
-- ya cargado se respeta. Excepción: el tipo de las consolas se fija siempre, porque es lo único que distingue una
-- venta de un arriendo. No se sube la versión de los productos (las ediciones pendientes no quedan desactualizadas).
--
-- Se conservan los slugs que ya existían (figuras, venta-videojuegos, accesorios-consolas) para no romper enlaces;
-- 'todos' sigue siendo la agrupadora de navegación sin filtro.

-- 1. Categorías generales (todas hojas: un producto se asigna directo a ellas)
INSERT INTO categoria (slug, titulo, parent_id, es_hoja, orden) VALUES
  ('tcg',      'TCG',      NULL, TRUE, 10),
  ('consolas', 'Consolas', NULL, TRUE, 40);

UPDATE categoria c SET es_hoja = TRUE, parent_id = NULL, titulo = v.titulo, orden = v.orden
FROM (VALUES
  ('figuras',             'Figuras',     20),
  ('venta-videojuegos',   'Videojuegos', 30),
  ('accesorios-consolas', 'Accesorios',  50)
) AS v(slug, titulo, orden)
WHERE c.slug = v.slug;

-- 2. De subcategoría vieja a categoría general + el campo que la reemplaza
CREATE TEMP TABLE mapa_categoria (
  viejo         VARCHAR(60) PRIMARY KEY,
  nuevo         VARCHAR(60) NOT NULL,
  franquicia    VARCHAR(60),
  plataforma    VARCHAR(30),
  tipo          VARCHAR(40),
  tipo_forzado  BOOLEAN     NOT NULL DEFAULT FALSE,
  marca         VARCHAR(60)
);   -- sin ON COMMIT DROP: así también se puede ejecutar a mano con psql (se borra en el paso 6)

INSERT INTO mapa_categoria (viejo, nuevo, franquicia, plataforma, tipo, tipo_forzado, marca) VALUES
  ('tcg-pokemon',              'tcg',                 'Pokémon',          NULL,                NULL,                  FALSE, NULL),
  ('tcg-one-piece',            'tcg',                 'One Piece',        NULL,                NULL,                  FALSE, NULL),
  ('tcg-mitos-y-leyendas',     'tcg',                 'Mitos y Leyendas', NULL,                NULL,                  FALSE, NULL),
  ('tcg-yu-gi-oh',             'tcg',                 'Yu-Gi-Oh!',        NULL,                NULL,                  FALSE, NULL),
  ('figuras-naruto',           'figuras',             'Naruto',           NULL,                NULL,                  FALSE, NULL),
  ('figuras-one-piece',        'figuras',             'One Piece',        NULL,                NULL,                  FALSE, NULL),
  ('figuras-pokemon',          'figuras',             'Pokémon',          NULL,                NULL,                  FALSE, NULL),
  ('figuras-dragon-ball',      'figuras',             'Dragon Ball',      NULL,                NULL,                  FALSE, NULL),
  ('figuras-demon-slayer',     'figuras',             'Demon Slayer',     NULL,                NULL,                  FALSE, NULL),
  ('figuras-jujutsu-kaisen',   'figuras',             'Jujutsu Kaisen',   NULL,                NULL,                  FALSE, NULL),
  ('figuras-my-hero-academia', 'figuras',             'My Hero Academia', NULL,                NULL,                  FALSE, NULL),
  ('figuras-attack-on-titan',  'figuras',             'Attack on Titan',  NULL,                NULL,                  FALSE, NULL),
  ('figuras-hunter-x-hunter',  'figuras',             'Hunter x Hunter',  NULL,                NULL,                  FALSE, NULL),
  ('figuras-bleach',           'figuras',             'Bleach',           NULL,                NULL,                  FALSE, NULL),
  ('figuras-funko-pop',        'figuras',             NULL,               NULL,                NULL,                  FALSE, 'Funko Pop'),
  ('figuras-otras',            'figuras',             NULL,               NULL,                NULL,                  FALSE, NULL),
  ('juegos-ps5',               'venta-videojuegos',   NULL,               'PlayStation 5',     NULL,                  FALSE, NULL),
  ('juegos-ps4',               'venta-videojuegos',   NULL,               'PlayStation 4',     NULL,                  FALSE, NULL),
  ('juegos-xbox-series',       'venta-videojuegos',   NULL,               'Xbox Series X|S',   NULL,                  FALSE, NULL),
  ('juegos-xbox-one',          'venta-videojuegos',   NULL,               'Xbox One',          NULL,                  FALSE, NULL),
  ('juegos-switch',            'venta-videojuegos',   NULL,               'Nintendo Switch',   NULL,                  FALSE, NULL),
  ('juegos-switch-2',          'venta-videojuegos',   NULL,               'Nintendo Switch 2', NULL,                  FALSE, NULL),
  ('juegos-retro',             'venta-videojuegos',   NULL,               'Retro',             NULL,                  FALSE, NULL),
  ('venta-consolas',           'consolas',            NULL,               NULL,                'Venta',               TRUE,  NULL),
  ('consolas-usadas',          'consolas',            NULL,               NULL,                'Usadas',              TRUE,  NULL),
  ('arriendo-consolas',        'consolas',            NULL,               NULL,                'Arriendo',            TRUE,  NULL),
  ('accesorios-controles',     'accesorios-consolas', NULL,               NULL,                'Controles',           FALSE, NULL),
  ('accesorios-audifonos',     'accesorios-consolas', NULL,               NULL,                'Audífonos',           FALSE, NULL),
  ('accesorios-fundas',        'accesorios-consolas', NULL,               NULL,                'Fundas y protección', FALSE, NULL),
  ('accesorios-cargadores',    'accesorios-consolas', NULL,               NULL,                'Cargadores y cables', FALSE, NULL);

-- 3. Productos (también los eliminados: el historial sigue apuntando a una categoría existente)
UPDATE producto p SET
  categoria_id  = cn.id,
  franquicia_id = COALESCE(p.franquicia_id, f.id),
  plataforma    = COALESCE(p.plataforma, m.plataforma),
  tipo          = CASE WHEN m.tipo_forzado THEN m.tipo ELSE COALESCE(p.tipo, m.tipo) END,
  marca         = COALESCE(p.marca, m.marca)
FROM mapa_categoria m
JOIN categoria cv ON cv.slug = m.viejo
JOIN categoria cn ON cn.slug = m.nuevo
LEFT JOIN franquicia f ON f.nombre = m.franquicia
WHERE p.categoria_id = cv.id;

-- 4. Ediciones propuestas por vendedores (JSON con el slug viejo): mismo cambio, para que se puedan aprobar
UPDATE producto_revision r SET datos_propuestos = r.datos_propuestos
    || jsonb_build_object('categoria', m.nuevo)
    || CASE WHEN m.franquicia IS NOT NULL AND r.datos_propuestos ->> 'franquicia' IS NULL
            THEN jsonb_build_object('franquicia', m.franquicia) ELSE '{}'::jsonb END
    || CASE WHEN m.plataforma IS NOT NULL AND r.datos_propuestos ->> 'plataforma' IS NULL
            THEN jsonb_build_object('plataforma', m.plataforma) ELSE '{}'::jsonb END
    || CASE WHEN m.tipo IS NOT NULL AND (m.tipo_forzado OR r.datos_propuestos ->> 'tipo' IS NULL)
            THEN jsonb_build_object('tipo', m.tipo) ELSE '{}'::jsonb END
    || CASE WHEN m.marca IS NOT NULL AND r.datos_propuestos ->> 'marca' IS NULL
            THEN jsonb_build_object('marca', m.marca) ELSE '{}'::jsonb END
FROM mapa_categoria m
WHERE r.datos_propuestos ->> 'categoria' = m.viejo;

-- 5. Sin productos que las usen, las subcategorías se borran
DELETE FROM categoria WHERE slug IN (SELECT viejo FROM mapa_categoria);

-- 6. Limpieza
DROP TABLE mapa_categoria;
