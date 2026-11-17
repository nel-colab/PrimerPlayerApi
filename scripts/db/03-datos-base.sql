-- =====================================================================================================
-- Primer Player · 03 · Datos base para operar (tienda real, sin datos de demostración)
--
-- Repone lo mínimo para usar la aplicación desde cero, igual que la migración V6:
--   categorías, franquicias, configuración de inventario (umbral de stock bajo = 3)
--   y el cliente anónimo de mostrador (necesario para ventas POS sin cliente registrado).
-- NO crea productos, eventos, pedidos ni usuarios (salvo el de mostrador, que no puede iniciar sesión).
--
-- Uso típico, para entregar la tienda limpia (Git Bash, desde la raíz del proyecto):
--   ./scripts/db/reiniciar-datos.sh --entrega          (= 01-limpiar-base.sql + este script)
-- Es seguro ejecutarlo más de una vez: no duplica nada.
-- =====================================================================================================

BEGIN;

DO $$
BEGIN
  IF current_database() <> 'primerplayer' THEN
    RAISE EXCEPTION 'Este script solo se ejecuta sobre la base local "primerplayer" (estás en "%").', current_database();
  END IF;
END $$;

INSERT INTO config_inventario (id, umbral_stock_bajo) VALUES (1, 3) ON CONFLICT (id) DO NOTHING;

INSERT INTO categoria (slug, titulo, parent_id, es_hoja, orden) VALUES
  ('todos',               'Todos',                  NULL, FALSE, 0),
  ('figuras',             'Figuras',                NULL, FALSE, 20),
  ('venta-videojuegos',   'Videojuegos',            NULL, FALSE, 30),
  ('accesorios-consolas', 'Accesorios de consolas', NULL, FALSE, 40),
  ('tcg-pokemon',          'TCG Pokémon',           NULL, TRUE, 1),
  ('tcg-one-piece',        'TCG One Piece',         NULL, TRUE, 2),
  ('tcg-mitos-y-leyendas', 'TCG Mitos y Leyendas',  NULL, TRUE, 3),
  ('tcg-yu-gi-oh',         'TCG Yu-Gi-Oh!',         NULL, TRUE, 4),
  ('venta-consolas',       'Venta de consolas',     NULL, TRUE, 10),
  ('consolas-usadas',      'Consolas usadas',       NULL, TRUE, 11),
  ('arriendo-consolas',    'Arriendo de consolas',  NULL, TRUE, 12)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO categoria (slug, titulo, parent_id, es_hoja, orden)
SELECT v.slug, v.titulo, (SELECT id FROM categoria WHERE slug = v.padre), TRUE, v.orden
FROM (VALUES
  ('figuras-naruto',           'Naruto',           'figuras', 1),
  ('figuras-one-piece',        'One Piece',        'figuras', 2),
  ('figuras-pokemon',          'Pokémon',          'figuras', 3),
  ('figuras-dragon-ball',      'Dragon Ball',      'figuras', 4),
  ('figuras-demon-slayer',     'Demon Slayer',     'figuras', 5),
  ('figuras-jujutsu-kaisen',   'Jujutsu Kaisen',   'figuras', 6),
  ('figuras-my-hero-academia', 'My Hero Academia', 'figuras', 7),
  ('figuras-attack-on-titan',  'Attack on Titan',  'figuras', 8),
  ('figuras-hunter-x-hunter',  'Hunter x Hunter',  'figuras', 9),
  ('figuras-bleach',           'Bleach',           'figuras', 10),
  ('figuras-funko-pop',        'Funko Pop',        'figuras', 11),
  ('figuras-otras',            'Otras figuras',    'figuras', 12),
  ('juegos-ps5',               'PS5',              'venta-videojuegos', 1),
  ('juegos-ps4',               'PS4',              'venta-videojuegos', 2),
  ('juegos-xbox-series',       'Xbox Series',      'venta-videojuegos', 3),
  ('juegos-xbox-one',          'Xbox One',         'venta-videojuegos', 4),
  ('juegos-switch',            'Switch',           'venta-videojuegos', 5),
  ('juegos-switch-2',          'Switch 2',         'venta-videojuegos', 6),
  ('juegos-retro',             'Retro',            'venta-videojuegos', 7),
  ('accesorios-controles',     'Controles',        'accesorios-consolas', 1),
  ('accesorios-audifonos',     'Audífonos',        'accesorios-consolas', 2),
  ('accesorios-fundas',        'Fundas',           'accesorios-consolas', 3),
  ('accesorios-cargadores',    'Cargadores',       'accesorios-consolas', 4)
) AS v(slug, titulo, padre, orden)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO franquicia (nombre) VALUES
  ('Pokémon'), ('One Piece'), ('Naruto'), ('Dragon Ball'), ('Demon Slayer'), ('Jujutsu Kaisen'),
  ('My Hero Academia'), ('Attack on Titan'), ('Hunter x Hunter'), ('Bleach'), ('Yu-Gi-Oh!'), ('Mitos y Leyendas')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO usuario (email, nombre, password_hash, rol, es_anonimo, email_verificado_en)
VALUES ('mostrador@primerplayer.cl', 'Venta en mostrador', NULL, 'CLIENTE', TRUE, now())
ON CONFLICT (email) DO NOTHING;


COMMIT;
