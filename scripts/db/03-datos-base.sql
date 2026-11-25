-- =====================================================================================================
-- Primer Player · 03 · Datos base para operar (tienda real, sin datos de demostración)
--
-- Repone lo mínimo para usar la aplicación desde cero, igual que las migraciones V6 y V13:
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

-- Categorías generales (V13): la subdivisión va por franquicia (TCG, Figuras), plataforma (Videojuegos)
-- o tipo (Consolas: Venta/Usadas/Arriendo; Accesorios). 'todos' es solo navegación.
INSERT INTO categoria (slug, titulo, parent_id, es_hoja, orden) VALUES
  ('todos',               'Todos',       NULL, FALSE, 0),
  ('tcg',                 'TCG',         NULL, TRUE,  10),
  ('figuras',             'Figuras',     NULL, TRUE,  20),
  ('venta-videojuegos',   'Videojuegos', NULL, TRUE,  30),
  ('consolas',            'Consolas',    NULL, TRUE,  40),
  ('accesorios-consolas', 'Accesorios',  NULL, TRUE,  50)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO franquicia (nombre) VALUES
  ('Pokémon'), ('One Piece'), ('Naruto'), ('Dragon Ball'), ('Demon Slayer'), ('Jujutsu Kaisen'),
  ('My Hero Academia'), ('Attack on Titan'), ('Hunter x Hunter'), ('Bleach'), ('Yu-Gi-Oh!'), ('Mitos y Leyendas')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO usuario (email, nombre, password_hash, rol, es_anonimo, email_verificado_en)
VALUES ('mostrador@primerplayer.cl', 'Venta en mostrador', NULL, 'CLIENTE', TRUE, now())
ON CONFLICT (email) DO NOTHING;


COMMIT;
