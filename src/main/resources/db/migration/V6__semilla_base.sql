-- Datos base para todos los ambientes: categorías, franquicias y cliente anónimo de mostrador.

-- Agrupadoras (solo navegación)
INSERT INTO categoria (slug, titulo, parent_id, es_hoja, orden) VALUES
  ('todos',               'Todos',                 NULL, FALSE, 0),
  ('figuras',             'Figuras',               NULL, FALSE, 20),
  ('venta-videojuegos',   'Videojuegos',           NULL, FALSE, 30),
  ('accesorios-consolas', 'Accesorios de consolas',NULL, FALSE, 40);

-- Hojas sin agrupadora
INSERT INTO categoria (slug, titulo, parent_id, es_hoja, orden) VALUES
  ('tcg-pokemon',          'TCG Pokémon',          NULL, TRUE, 1),
  ('tcg-one-piece',        'TCG One Piece',        NULL, TRUE, 2),
  ('tcg-mitos-y-leyendas', 'TCG Mitos y Leyendas', NULL, TRUE, 3),
  ('tcg-yu-gi-oh',         'TCG Yu-Gi-Oh!',        NULL, TRUE, 4),
  ('venta-consolas',       'Venta de consolas',    NULL, TRUE, 10),
  ('consolas-usadas',      'Consolas usadas',      NULL, TRUE, 11),
  ('arriendo-consolas',    'Arriendo de consolas', NULL, TRUE, 12);

-- Hojas de figuras
INSERT INTO categoria (slug, titulo, parent_id, es_hoja, orden)
SELECT v.slug, v.titulo, (SELECT id FROM categoria WHERE slug = 'figuras'), TRUE, v.orden
FROM (VALUES
  ('figuras-naruto',           'Naruto',            1),
  ('figuras-one-piece',        'One Piece',         2),
  ('figuras-pokemon',          'Pokémon',           3),
  ('figuras-dragon-ball',      'Dragon Ball',       4),
  ('figuras-demon-slayer',     'Demon Slayer',      5),
  ('figuras-jujutsu-kaisen',   'Jujutsu Kaisen',    6),
  ('figuras-my-hero-academia', 'My Hero Academia',  7),
  ('figuras-attack-on-titan',  'Attack on Titan',   8),
  ('figuras-hunter-x-hunter',  'Hunter x Hunter',   9),
  ('figuras-bleach',           'Bleach',           10),
  ('figuras-funko-pop',        'Funko Pop',        11),
  ('figuras-otras',            'Otras figuras',    12)
) AS v(slug, titulo, orden);

-- Hojas de videojuegos
INSERT INTO categoria (slug, titulo, parent_id, es_hoja, orden)
SELECT v.slug, v.titulo, (SELECT id FROM categoria WHERE slug = 'venta-videojuegos'), TRUE, v.orden
FROM (VALUES
  ('juegos-ps5',         'PS5',          1),
  ('juegos-ps4',         'PS4',          2),
  ('juegos-xbox-series', 'Xbox Series',  3),
  ('juegos-xbox-one',    'Xbox One',     4),
  ('juegos-switch',      'Switch',       5),
  ('juegos-switch-2',    'Switch 2',     6),
  ('juegos-retro',       'Retro',        7)
) AS v(slug, titulo, orden);

-- Hojas de accesorios
INSERT INTO categoria (slug, titulo, parent_id, es_hoja, orden)
SELECT v.slug, v.titulo, (SELECT id FROM categoria WHERE slug = 'accesorios-consolas'), TRUE, v.orden
FROM (VALUES
  ('accesorios-controles',  'Controles',  1),
  ('accesorios-audifonos',  'Audífonos',  2),
  ('accesorios-fundas',     'Fundas',     3),
  ('accesorios-cargadores', 'Cargadores', 4)
) AS v(slug, titulo, orden);

INSERT INTO franquicia (nombre) VALUES
  ('Pokémon'), ('One Piece'), ('Naruto'), ('Dragon Ball'), ('Demon Slayer'), ('Jujutsu Kaisen'),
  ('My Hero Academia'), ('Attack on Titan'), ('Hunter x Hunter'), ('Bleach'), ('Yu-Gi-Oh!'), ('Mitos y Leyendas');

-- Cliente anónimo para ventas de mostrador (sin contraseña: no puede iniciar sesión)
INSERT INTO usuario (email, nombre, password_hash, rol, es_anonimo, email_verificado_en)
VALUES ('mostrador@primerplayer.cl', 'Venta en mostrador', NULL, 'CLIENTE', TRUE, now());
