-- =====================================================================================================
-- Primer Player · 02 · Poblar la base con datos de demostración (SOLO DESARROLLO LOCAL Y STAGING)
--
-- Bases permitidas: "primerplayer" (local) y "runagard_staging" (staging). Producción ("runagard") nunca.
--
-- Llena TODAS las tablas de negocio con datos realistas y coherentes entre sí, para recorrer la aplicación
-- completa: catálogo, stock, eventos, cumpleaños, pedidos web y de mostrador, Webpay, caja, auditoría y correos.
--
-- Requisitos: la base debe estar vacía (corre antes `01-limpiar-base.sql`).
-- Todo corre en UNA transacción: si algo falla o una validación final no cuadra, no queda nada a medias.
--
-- Fechas relativas a HOY (hora de Chile): los eventos siempre quedan en el futuro y el historial en el pasado,
-- da igual el día en que lo ejecutes. Las ventas se generan de forma determinista (mismo día = mismos datos).
--
-- Cuentas (contraseña de todas: PrimerPlayer2026!):
--   admin@primerplayer.cl (ADMIN) · vendedor@primerplayer.cl (VENDEDOR) · cliente@primerplayer.cl (CLIENTE)
--   vendedora@demo.primerplayer.cl (VENDEDOR) · clientes demo *@demo.primerplayer.cl
--
-- Parámetros opcionales (psql -v):
--   escala=N   (1 a 50, por defecto 1) multiplica el volumen para pruebas de carga: N veces las ventas diarias del
--              historial, (N−1) copias del catálogo como productos adicionales y (N−1)×10 clientes extra
--              (carga-NNNN@demo.primerplayer.cl). Con escala=1 los datos son exactamente los de siempre.
--   clave=...  contraseña para TODAS las cuentas (en vez de PrimerPlayer2026!). Obligatoria en staging: es público.
--
-- Uso (Git Bash, desde la raíz del proyecto):
--   docker compose exec -T postgres psql -U pp -d primerplayer -v ON_ERROR_STOP=1 < scripts/db/02-poblar-base.sql
--   Staging: ./scripts/db/poblar-staging.sh   (corre 01 + 02 en el VPS por SSH)
-- =====================================================================================================

\if :{?escala}
\else
  \set escala 1
\endif

BEGIN;

SELECT set_config('demo.escala', :'escala', false);

-- -----------------------------------------------------------------------------------------------------
-- 0. Precondición: base vacía
--    (se toleran las 3 cuentas dev que crea la app al arrancar y el cliente de mostrador)
-- -----------------------------------------------------------------------------------------------------
DO $$
BEGIN
  IF current_database() NOT IN ('primerplayer', 'runagard_staging') THEN
    RAISE EXCEPTION 'Este script solo se ejecuta en local ("primerplayer") o staging ("runagard_staging"); estás en "%".',
      current_database();
  END IF;
  IF current_setting('demo.escala') !~ '^[0-9]+$' OR current_setting('demo.escala')::int NOT BETWEEN 1 AND 50 THEN
    RAISE EXCEPTION 'escala debe ser un entero entre 1 y 50 (llegó "%").', current_setting('demo.escala');
  END IF;
  IF EXISTS (SELECT 1 FROM producto) OR EXISTS (SELECT 1 FROM evento) OR EXISTS (SELECT 1 FROM pedido)
     OR EXISTS (SELECT 1 FROM usuario WHERE email NOT IN ('mostrador@primerplayer.cl', 'admin@primerplayer.cl',
                                                         'vendedor@primerplayer.cl', 'cliente@primerplayer.cl')) THEN
    RAISE EXCEPTION 'La base no está vacía. Ejecuta primero scripts/db/01-limpiar-base.sql';
  END IF;
END $$;

-- -----------------------------------------------------------------------------------------------------
-- Funciones auxiliares (temporales: desaparecen al cerrar la sesión)
-- -----------------------------------------------------------------------------------------------------

-- Número pseudoaleatorio determinista en [0, 1) a partir de un texto.
CREATE OR REPLACE FUNCTION pg_temp.rnd(s text) RETURNS float8 LANGUAGE sql IMMUTABLE AS
$$ SELECT (hashtext(s)::bigint + 2147483648) / 4294967296.0 $$;

-- Hoy en Chile.
CREATE OR REPLACE FUNCTION pg_temp.hoy() RETURNS date LANGUAGE sql STABLE AS
$$ SELECT (now() AT TIME ZONE 'America/Santiago')::date $$;

-- Instante para un día y una hora local de Chile (h = 15.5 → 15:30).
CREATE OR REPLACE FUNCTION pg_temp.ts(d date, h float8) RETURNS timestamptz LANGUAGE sql STABLE AS
$$ SELECT (d + make_interval(secs => h * 3600)) AT TIME ZONE 'America/Santiago' $$;

-- EAN-13 válido (con dígito verificador) a partir de 12 dígitos.
CREATE OR REPLACE FUNCTION pg_temp.ean13(base12 text) RETURNS text LANGUAGE sql IMMUTABLE AS $$
  SELECT base12 || ((10 - (SUM(substr(base12, i, 1)::int * CASE WHEN i % 2 = 0 THEN 3 ELSE 1 END) % 10)) % 10)::text
  FROM generate_series(1, 12) AS i
$$;

-- RUT chileno con dígito verificador (módulo 11).
CREATE OR REPLACE FUNCTION pg_temp.rut(n int) RETURNS text LANGUAGE sql IMMUTABLE AS $$
  SELECT n || '-' || CASE r WHEN 11 THEN '0' WHEN 10 THEN 'K' ELSE r::text END
  FROM (SELECT 11 - (SUM(substr(reverse(n::text), i, 1)::int * (2 + (i - 1) % 6)) % 11) AS r
          FROM generate_series(1, length(n::text)) AS i) x
$$;

CREATE OR REPLACE FUNCTION pg_temp.uid(p_email text) RETURNS bigint LANGUAGE plpgsql STABLE AS $$
DECLARE v bigint;
BEGIN
  SELECT id INTO v FROM usuario WHERE email = p_email;
  IF v IS NULL THEN RAISE EXCEPTION 'Usuario demo no encontrado: %', p_email; END IF;
  RETURN v;
END $$;

CREATE OR REPLACE FUNCTION pg_temp.pid(p_nombre text) RETURNS bigint LANGUAGE plpgsql STABLE AS $$
DECLARE v bigint;
BEGIN
  SELECT id INTO v FROM producto WHERE nombre = p_nombre;
  IF v IS NULL THEN RAISE EXCEPTION 'Producto demo no encontrado: %', p_nombre; END IF;
  RETURN v;
END $$;

CREATE OR REPLACE FUNCTION pg_temp.eid(p_titulo text) RETURNS bigint LANGUAGE plpgsql STABLE AS $$
DECLARE v bigint;
BEGIN
  SELECT id INTO v FROM evento WHERE titulo = p_titulo;
  IF v IS NULL THEN RAISE EXCEPTION 'Evento demo no encontrado: %', p_titulo; END IF;
  RETURN v;
END $$;

-- Kardex en borrador: se arma aquí y al final se vuelca a movimiento_stock con el stock resultante calculado.
CREATE TEMP TABLE demo_mov (
  seq         int GENERATED ALWAYS AS IDENTITY,
  producto_id bigint NOT NULL,
  tipo        text   NOT NULL,
  cantidad    int    NOT NULL,
  pedido_id   bigint,
  motivo      text,
  usuario_id  bigint,
  creado_en   timestamptz NOT NULL
) ON COMMIT DROP;

CREATE TEMP SEQUENCE demo_pedido_tmp;               -- código provisorio; al final se renumera por fecha
CREATE TEMP SEQUENCE demo_boleta START 104501;      -- números de boleta de débito/crédito/prepago

-- =====================================================================================================
-- 1. Datos base (los mismos de V6 + V13: categorías, franquicias, configuración y cliente de mostrador)
-- =====================================================================================================
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

-- =====================================================================================================
-- 2. Usuarios, direcciones y tokens
-- =====================================================================================================
-- Hash bcrypt de "PrimerPlayer2026!" (el mismo que genera la app en el perfil dev).
INSERT INTO usuario (email, nombre, password_hash, rol, activo, email_verificado_en, telefono, rut,
                     intentos_fallidos, bloqueado_hasta, creado_en, actualizado_en, eliminado_en)
SELECT v.email, v.nombre, '$2a$12$VOdHD72fQRUVG4Hr4W1SZ.L0Toz4ijrG5xNIdG3GDrtsnQaBIPWiS', v.rol, v.activo,
       CASE WHEN v.verificado THEN now() - make_interval(days => v.dias) + interval '10 minutes' END,
       v.telefono, CASE WHEN v.rut IS NOT NULL THEN pg_temp.rut(v.rut) END,
       CASE WHEN v.bloqueado THEN 5 ELSE 0 END,
       CASE WHEN v.bloqueado THEN now() + interval '7 days' END,   -- bloqueo largo solo para verlo en la demo
       now() - make_interval(days => v.dias),
       now() - make_interval(days => least(v.dias, 3)),
       CASE WHEN v.eliminado THEN now() - interval '12 days' END
FROM (VALUES
  -- email                                     nombre                rol         activo verif. teléfono             rut       bloq.  elim.  días
  ('admin@primerplayer.cl',                   'Admin Demo',         'ADMIN',    TRUE,  TRUE,  NULL::text,          NULL::int, FALSE, FALSE, 310),
  ('vendedor@primerplayer.cl',                'Vendedor Demo',      'VENDEDOR', TRUE,  TRUE,  '+56 9 5555 1111',   NULL,     FALSE, FALSE, 305),
  ('cliente@primerplayer.cl',                 'Cliente Demo',       'CLIENTE',  TRUE,  TRUE,  '+56 9 1234 5678',   12345678, FALSE, FALSE, 270),
  ('vendedora@demo.primerplayer.cl',          'Javiera Muñoz',      'VENDEDOR', TRUE,  TRUE,  '+56 9 5555 2222',   17654321, FALSE, FALSE, 120),
  ('camila.rojas@demo.primerplayer.cl',       'Camila Rojas',       'CLIENTE',  TRUE,  TRUE,  '+56 9 8765 4321',   19876543, FALSE, FALSE, 250),
  ('valentina.perez@demo.primerplayer.cl',    'Valentina Pérez',    'CLIENTE',  TRUE,  TRUE,  '+56 9 7311 2045',   20123456, FALSE, FALSE, 230),
  ('benjamin.gonzalez@demo.primerplayer.cl',  'Benjamín González',  'CLIENTE',  TRUE,  TRUE,  NULL,                18456789, FALSE, FALSE, 210),
  ('agustin.diaz@demo.primerplayer.cl',       'Agustín Díaz',       'CLIENTE',  TRUE,  TRUE,  '+56 9 6620 1188',   NULL,     FALSE, FALSE, 190),
  ('isidora.fuentes@demo.primerplayer.cl',    'Isidora Fuentes',    'CLIENTE',  TRUE,  TRUE,  '+56 9 5402 7731',   21034567, FALSE, FALSE, 180),
  ('tomas.silva@demo.primerplayer.cl',        'Tomás Silva',        'CLIENTE',  TRUE,  TRUE,  '+56 9 9183 4420',   19345678, FALSE, FALSE, 170),
  ('florencia.castro@demo.primerplayer.cl',   'Florencia Castro',   'CLIENTE',  TRUE,  TRUE,  '+56 9 4471 9902',   NULL,     FALSE, FALSE, 150),
  ('vicente.reyes@demo.primerplayer.cl',      'Vicente Reyes',      'CLIENTE',  TRUE,  TRUE,  NULL,                NULL,     FALSE, FALSE, 140),
  ('martina.morales@demo.primerplayer.cl',    'Martina Morales',    'CLIENTE',  TRUE,  TRUE,  '+56 9 3390 1276',   20987654, FALSE, FALSE, 120),
  ('fernanda.navarro@demo.primerplayer.cl',   'Fernanda Navarro',   'CLIENTE',  TRUE,  TRUE,  '+56 9 8012 3345',   NULL,     FALSE, FALSE, 100),
  ('diego.alarcon@demo.primerplayer.cl',      'Diego Alarcón',      'CLIENTE',  TRUE,  TRUE,  '+56 9 7720 6618',   18765432, FALSE, FALSE, 95),
  -- casos especiales de cuenta
  ('matias.soto@demo.primerplayer.cl',        'Matías Soto',        'CLIENTE',  TRUE,  FALSE, NULL,                NULL,     FALSE, FALSE, 11),   -- sin verificar
  ('joaquin.herrera@demo.primerplayer.cl',    'Joaquín Herrera',    'CLIENTE',  TRUE,  TRUE,  '+56 9 2210 4455',   NULL,     TRUE,  FALSE, 160),  -- bloqueado
  ('antonia.vargas@demo.primerplayer.cl',     'Antonia Vargas',     'CLIENTE',  FALSE, TRUE,  NULL,                19555444, FALSE, FALSE, 200),  -- inactiva
  ('lucas.torres@demo.primerplayer.cl',       'Lucas Torres',       'CLIENTE',  TRUE,  TRUE,  NULL,                NULL,     FALSE, TRUE,  220)   -- eliminado
) AS v(email, nombre, rol, activo, verificado, telefono, rut, bloqueado, eliminado, dias)
ON CONFLICT (email) DO NOTHING;   -- si la app ya creó las 3 cuentas dev, se conservan

-- Clientes extra para pruebas de carga (solo con escala > 1): verificados, activos y con la misma contraseña.
INSERT INTO usuario (email, nombre, password_hash, rol, activo, email_verificado_en, creado_en, actualizado_en)
SELECT format('carga-%s@demo.primerplayer.cl', lpad(i::text, 4, '0')),
       (ARRAY['Sofía','Mateo','Emilia','Benjamín','Josefa','Lucas','Trinidad','Gaspar','Amanda','Maximiliano'])[1 + i % 10]
         || ' ' ||
       (ARRAY['González','Muñoz','Rojas','Díaz','Pérez','Soto','Contreras','Silva','Martínez','Sepúlveda'])[1 + (i / 10) % 10],
       '$2a$12$VOdHD72fQRUVG4Hr4W1SZ.L0Toz4ijrG5xNIdG3GDrtsnQaBIPWiS', 'CLIENTE', TRUE,
       now() - make_interval(days => 1 + i % 300) + interval '10 minutes',
       now() - make_interval(days => 1 + i % 300),
       now() - make_interval(days => 1 + i % 300)
FROM generate_series(1, (current_setting('demo.escala')::int - 1) * 10) AS i
ON CONFLICT (email) DO NOTHING;

-- V9: baneo, lista negra, cambio de contraseña pendiente y último acceso
-- Joaquín ya tenía bloqueo automático (intentos fallidos); aquí además recibe un baneo manual.
UPDATE usuario SET
  baneo_motivo    = 'Reiteradas solicitudes de devolución con comprobantes apócrifos.',
  baneo_hasta     = now() + interval '90 days',
  baneo_creado_en = now() - interval '5 days'
WHERE email = 'joaquin.herrera@demo.primerplayer.cl';

-- Florencia en lista negra: puede iniciar sesión pero no puede comprar ni solicitar cumpleaños.
UPDATE usuario SET
  lista_negra_motivo = 'Intentó usar un comprobante de transferencia falsificado.',
  lista_negra_en     = now() - interval '12 days'
WHERE email = 'florencia.castro@demo.primerplayer.cl';

-- Matías (sin verificar) tiene además cambio de contraseña pendiente: doble caso para probar el flujo.
UPDATE usuario SET cambio_password_pendiente = TRUE
WHERE email = 'matias.soto@demo.primerplayer.cl';

-- Un segundo caso de cambio de contraseña pendiente: Fernanda (cuenta en buen estado, solo forzado por admin).
UPDATE usuario SET cambio_password_pendiente = TRUE
WHERE email = 'fernanda.navarro@demo.primerplayer.cl';

-- ultimo_acceso_en se calcula al final de la sección 2 solo con refresh_token (los pedidos aún no existen).
-- El resto del cálculo (desde pedidos) se aplica al final del script, después de la sección 8.
UPDATE usuario u SET ultimo_acceso_en = rt.ultimo
FROM (SELECT usuario_id, max(creado_en) AS ultimo FROM refresh_token GROUP BY usuario_id) rt
WHERE u.id = rt.usuario_id;

INSERT INTO direccion (usuario_id, alias, calle, comuna, region, referencia, principal)
SELECT pg_temp.uid(v.email), v.alias, v.calle, v.comuna, 'Región Metropolitana', v.referencia, v.principal
FROM (VALUES
  ('cliente@primerplayer.cl',               'Casa',    'Freire 456',                    'San Bernardo', 'Portón verde',          TRUE),
  ('cliente@primerplayer.cl',               'Trabajo', 'Av. Providencia 1234, of. 502', 'Providencia',  NULL,                    FALSE),
  ('camila.rojas@demo.primerplayer.cl',     'Casa',    'Eyzaguirre 1020',               'San Bernardo', NULL,                    TRUE),
  ('valentina.perez@demo.primerplayer.cl',  'Depto',   'Av. Colón 3100, depto 41',      'San Bernardo', 'Torre B',               TRUE),
  ('isidora.fuentes@demo.primerplayer.cl',  'Casa',    'Los Aromos 77',                 'Buin',         NULL,                    TRUE),
  ('tomas.silva@demo.primerplayer.cl',      'Casa',    'Calle Nueva 845',               'Calera de Tango', 'Frente a la plaza',  TRUE),
  ('diego.alarcon@demo.primerplayer.cl',    'Casa',    'Gran Avenida 12890',            'El Bosque',    NULL,                    TRUE),
  ('diego.alarcon@demo.primerplayer.cl',    'Abuela',  'Pasaje Los Olmos 12',           'San Bernardo', NULL,                    FALSE),
  ('martina.morales@demo.primerplayer.cl',  'Casa',    'Av. Padre Hurtado 990',         'San Bernardo', NULL,                    TRUE)
) AS v(email, alias, calle, comuna, referencia, principal);

-- Tokens de un uso: verificación pendiente, recuperación usada y recuperación vencida.
INSERT INTO token_un_uso (id, usuario_id, tipo, token_hash, expira_en, usado_en)
VALUES
  (gen_random_uuid(), pg_temp.uid('matias.soto@demo.primerplayer.cl'), 'VERIFICACION',
   encode(sha256(convert_to('demo-verificacion-matias', 'UTF8')), 'hex'), now() + interval '20 hours', NULL),
  (gen_random_uuid(), pg_temp.uid('camila.rojas@demo.primerplayer.cl'), 'RECUPERACION',
   encode(sha256(convert_to('demo-recuperacion-camila', 'UTF8')), 'hex'), now() - interval '29 days', now() - interval '30 days'),
  (gen_random_uuid(), pg_temp.uid('joaquin.herrera@demo.primerplayer.cl'), 'RECUPERACION',
   encode(sha256(convert_to('demo-recuperacion-joaquin', 'UTF8')), 'hex'), now() - interval '2 days', NULL);

-- Sesiones antiguas (revocadas o vencidas): solo historial, ninguna sirve para entrar.
INSERT INTO refresh_token (id, usuario_id, familia, token_hash, expira_en, revocado_en, reemplazado_por, creado_en, ip, user_agent)
SELECT gen_random_uuid(), pg_temp.uid(v.email), gen_random_uuid(),
       encode(sha256(convert_to('demo-refresh-' || v.email || v.n, 'UTF8')), 'hex'),
       now() - make_interval(days => v.dias) + interval '30 days',
       CASE WHEN v.revocado THEN now() - make_interval(days => v.dias) + interval '2 hours' END,
       NULL, now() - make_interval(days => v.dias), v.ip::inet, v.agente
FROM (VALUES
  ('admin@primerplayer.cl',   1, 10, TRUE,  '190.161.24.10', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/142.0'),
  ('cliente@primerplayer.cl', 1, 45, FALSE, '181.43.120.7',  'Mozilla/5.0 (iPhone; CPU iPhone OS 19_1) Safari/605.1'),
  ('cliente@primerplayer.cl', 2, 6,  TRUE,  '181.43.120.7',  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Firefox/145.0')
) AS v(email, n, dias, revocado, ip, agente);

-- =====================================================================================================
-- 3. Catálogo (93 productos de PrimerPlayerFront/src/app/core/data/mock-products.ts)
-- =====================================================================================================
CREATE TEMP TABLE demo_producto (
  n int GENERATED ALWAYS AS IDENTITY, nombre text, precio bigint, categoria text, tipo text, idioma text,
  marca text, plataforma text, franquicia text, linea text, limite int, stock int, popularidad int, dias int,
  descripcion text
) ON COMMIT DROP;

INSERT INTO demo_producto (nombre, precio, categoria, tipo, idioma, marca, plataforma, franquicia, linea,
                           limite, stock, popularidad, dias, descripcion)
VALUES
  ('Sobre Pokémon Tinieblas Umbrías (Español)', 6990, 'tcg', 'sobres', 'Español', NULL, NULL, 'Pokémon', 'TCG', NULL, 2, 37, 47, 'Abre nuevas posibilidades para tu colección con Sobre Pokémon Tinieblas Umbrías (Español). Cada sobre invita a descubrir cartas y disfrutar la emoción de ampliar tu estrategia en Pokémon.

## Detalles
- Producto: Sobre Pokémon Tinieblas Umbrías (Español)
- Franquicia: Pokémon
- Tipo: sobres
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Sobre Pokémon Tinieblas Umbrías (Inglés)', 6990, 'tcg', 'sobres', 'Inglés', NULL, NULL, 'Pokémon', 'TCG', NULL, 3, 74, 54, 'Abre nuevas posibilidades para tu colección con Sobre Pokémon Tinieblas Umbrías (Inglés). Cada sobre invita a descubrir cartas y disfrutar la emoción de ampliar tu estrategia en Pokémon.

## Detalles
- Producto: Sobre Pokémon Tinieblas Umbrías (Inglés)
- Franquicia: Pokémon
- Tipo: sobres
- Idioma: Inglés

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Sobre Pokémon Phantasmal Flames (Español)', 6990, 'tcg', 'sobres', 'Español', NULL, NULL, 'Pokémon', 'TCG', NULL, 4, 11, 1, 'Abre nuevas posibilidades para tu colección con Sobre Pokémon Phantasmal Flames (Español). Cada sobre invita a descubrir cartas y disfrutar la emoción de ampliar tu estrategia en Pokémon.

## Detalles
- Producto: Sobre Pokémon Phantasmal Flames (Español)
- Franquicia: Pokémon
- Tipo: sobres
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Sobre Pokémon Phantasmal Flames (Inglés)', 6990, 'tcg', 'sobres', 'Inglés', NULL, NULL, 'Pokémon', 'TCG', NULL, 5, 48, 8, 'Abre nuevas posibilidades para tu colección con Sobre Pokémon Phantasmal Flames (Inglés). Cada sobre invita a descubrir cartas y disfrutar la emoción de ampliar tu estrategia en Pokémon.

## Detalles
- Producto: Sobre Pokémon Phantasmal Flames (Inglés)
- Franquicia: Pokémon
- Tipo: sobres
- Idioma: Inglés

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Sobre Pokémon Chaos Rising (Español)', 6990, 'tcg', 'sobres', 'Español', NULL, NULL, 'Pokémon', 'TCG', NULL, 1, 85, 15, 'Abre nuevas posibilidades para tu colección con Sobre Pokémon Chaos Rising (Español). Cada sobre invita a descubrir cartas y disfrutar la emoción de ampliar tu estrategia en Pokémon.

## Detalles
- Producto: Sobre Pokémon Chaos Rising (Español)
- Franquicia: Pokémon
- Tipo: sobres
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Sobre Pokémon Chaos Rising (Inglés)', 6990, 'tcg', 'sobres', 'Inglés', NULL, NULL, 'Pokémon', 'TCG', NULL, 2, 22, 22, 'Abre nuevas posibilidades para tu colección con Sobre Pokémon Chaos Rising (Inglés). Cada sobre invita a descubrir cartas y disfrutar la emoción de ampliar tu estrategia en Pokémon.

## Detalles
- Producto: Sobre Pokémon Chaos Rising (Inglés)
- Franquicia: Pokémon
- Tipo: sobres
- Idioma: Inglés

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Sobre Pokémon Ascended Heroes (Español)', 6990, 'tcg', 'sobres', 'Español', NULL, NULL, 'Pokémon', 'TCG', NULL, 3, 59, 29, 'Abre nuevas posibilidades para tu colección con Sobre Pokémon Ascended Heroes (Español). Cada sobre invita a descubrir cartas y disfrutar la emoción de ampliar tu estrategia en Pokémon.

## Detalles
- Producto: Sobre Pokémon Ascended Heroes (Español)
- Franquicia: Pokémon
- Tipo: sobres
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Sobre Pokémon Ascended Heroes (Inglés)', 6990, 'tcg', 'sobres', 'Inglés', NULL, NULL, 'Pokémon', 'TCG', NULL, 0, 96, 36, 'Abre nuevas posibilidades para tu colección con Sobre Pokémon Ascended Heroes (Inglés). Cada sobre invita a descubrir cartas y disfrutar la emoción de ampliar tu estrategia en Pokémon.

## Detalles
- Producto: Sobre Pokémon Ascended Heroes (Inglés)
- Franquicia: Pokémon
- Tipo: sobres
- Idioma: Inglés

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Sobre Pokémon Mega Evolution (Español)', 6990, 'tcg', 'sobres', 'Español', NULL, NULL, 'Pokémon', 'TCG', NULL, 5, 33, 43, 'Abre nuevas posibilidades para tu colección con Sobre Pokémon Mega Evolution (Español). Cada sobre invita a descubrir cartas y disfrutar la emoción de ampliar tu estrategia en Pokémon.

## Detalles
- Producto: Sobre Pokémon Mega Evolution (Español)
- Franquicia: Pokémon
- Tipo: sobres
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Sobre Pokémon Mega Evolution (Inglés)', 6990, 'tcg', 'sobres', 'Inglés', NULL, NULL, 'Pokémon', 'TCG', NULL, 1, 70, 50, 'Abre nuevas posibilidades para tu colección con Sobre Pokémon Mega Evolution (Inglés). Cada sobre invita a descubrir cartas y disfrutar la emoción de ampliar tu estrategia en Pokémon.

## Detalles
- Producto: Sobre Pokémon Mega Evolution (Inglés)
- Franquicia: Pokémon
- Tipo: sobres
- Idioma: Inglés

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Sobre Pokémon Black Bolt (Español)', 6990, 'tcg', 'sobres', 'Español', NULL, NULL, 'Pokémon', 'TCG', 8, 2, 7, 57, 'Abre nuevas posibilidades para tu colección con Sobre Pokémon Black Bolt (Español). Cada sobre invita a descubrir cartas y disfrutar la emoción de ampliar tu estrategia en Pokémon.

## Detalles
- Producto: Sobre Pokémon Black Bolt (Español)
- Franquicia: Pokémon
- Tipo: sobres
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Sobre Pokémon Black Bolt (Inglés)', 6990, 'tcg', 'sobres', 'Inglés', NULL, NULL, 'Pokémon', 'TCG', 8, 3, 44, 4, 'Abre nuevas posibilidades para tu colección con Sobre Pokémon Black Bolt (Inglés). Cada sobre invita a descubrir cartas y disfrutar la emoción de ampliar tu estrategia en Pokémon.

## Detalles
- Producto: Sobre Pokémon Black Bolt (Inglés)
- Franquicia: Pokémon
- Tipo: sobres
- Idioma: Inglés

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Mazo Mega League Battler Mewtwo ex', 34990, 'tcg', 'mazos', 'Inglés', NULL, NULL, 'Pokémon', 'TCG', NULL, 4, 81, 11, 'Prepárate para jugar con Mazo Mega League Battler Mewtwo ex, una opción para quienes quieren llevar su pasión por Pokémon directamente a la mesa y disfrutar cada enfrentamiento.

## Detalles
- Producto: Mazo Mega League Battler Mewtwo ex
- Franquicia: Pokémon
- Tipo: mazos
- Idioma: Inglés

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Build & Battle Pokémon Chaos Rising', 34990, 'tcg', 'mazos', 'Inglés', NULL, NULL, 'Pokémon', 'TCG', NULL, 5, 18, 18, 'Prepárate para jugar con Build & Battle Pokémon Chaos Rising, una opción para quienes quieren llevar su pasión por Pokémon directamente a la mesa y disfrutar cada enfrentamiento.

## Detalles
- Producto: Build & Battle Pokémon Chaos Rising
- Franquicia: Pokémon
- Tipo: mazos
- Idioma: Inglés

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Mazo Battle Deck Charizard ex', 24990, 'tcg', 'mazos', 'Español', NULL, NULL, 'Pokémon', 'TCG', NULL, 1, 55, 25, 'Prepárate para jugar con Mazo Battle Deck Charizard ex, una opción para quienes quieren llevar su pasión por Pokémon directamente a la mesa y disfrutar cada enfrentamiento.

## Detalles
- Producto: Mazo Battle Deck Charizard ex
- Franquicia: Pokémon
- Tipo: mazos
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Mazo Battle Deck Lucario ex', 24990, 'tcg', 'mazos', 'Español', NULL, NULL, 'Pokémon', 'TCG', NULL, 2, 92, 32, 'Prepárate para jugar con Mazo Battle Deck Lucario ex, una opción para quienes quieren llevar su pasión por Pokémon directamente a la mesa y disfrutar cada enfrentamiento.

## Detalles
- Producto: Mazo Battle Deck Lucario ex
- Franquicia: Pokémon
- Tipo: mazos
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Mazo de Inicio Mega Evolution', 19990, 'tcg', 'mazos', 'Español', NULL, NULL, 'Pokémon', 'TCG', NULL, 0, 29, 39, 'Prepárate para jugar con Mazo de Inicio Mega Evolution, una opción para quienes quieren llevar su pasión por Pokémon directamente a la mesa y disfrutar cada enfrentamiento.

## Detalles
- Producto: Mazo de Inicio Mega Evolution
- Franquicia: Pokémon
- Tipo: mazos
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Mazo Liga Gardevoir ex', 29990, 'tcg', 'mazos', 'Inglés', NULL, NULL, 'Pokémon', 'TCG', NULL, 4, 66, 46, 'Prepárate para jugar con Mazo Liga Gardevoir ex, una opción para quienes quieren llevar su pasión por Pokémon directamente a la mesa y disfrutar cada enfrentamiento.

## Detalles
- Producto: Mazo Liga Gardevoir ex
- Franquicia: Pokémon
- Tipo: mazos
- Idioma: Inglés

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Elite Trainer Box Phantasmal Flames', 59990, 'tcg', 'cajas', 'Inglés', NULL, NULL, 'Pokémon', 'TCG', 2, 5, 3, 53, 'Dale un impulso a tu colección con Elite Trainer Box Phantasmal Flames. Su formato reúne una atractiva propuesta de Pokémon para disfrutar, coleccionar y compartir tu pasión por las cartas.

## Detalles
- Producto: Elite Trainer Box Phantasmal Flames
- Franquicia: Pokémon
- Tipo: cajas
- Idioma: Inglés

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Elite Trainer Box Ascended Heroes', 64990, 'tcg', 'cajas', 'Español', NULL, NULL, 'Pokémon', 'TCG', 2, 1, 40, 0, 'Dale un impulso a tu colección con Elite Trainer Box Ascended Heroes. Su formato reúne una atractiva propuesta de Pokémon para disfrutar, coleccionar y compartir tu pasión por las cartas.

## Detalles
- Producto: Elite Trainer Box Ascended Heroes
- Franquicia: Pokémon
- Tipo: cajas
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Elite Trainer Box Mega Evolution', 59990, 'tcg', 'cajas', 'Español', NULL, NULL, 'Pokémon', 'TCG', 2, 2, 77, 7, 'Dale un impulso a tu colección con Elite Trainer Box Mega Evolution. Su formato reúne una atractiva propuesta de Pokémon para disfrutar, coleccionar y compartir tu pasión por las cartas.

## Detalles
- Producto: Elite Trainer Box Mega Evolution
- Franquicia: Pokémon
- Tipo: cajas
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Elite Trainer Box Chaos Rising', 59990, 'tcg', 'cajas', 'Inglés', NULL, NULL, 'Pokémon', 'TCG', 2, 3, 14, 14, 'Dale un impulso a tu colección con Elite Trainer Box Chaos Rising. Su formato reúne una atractiva propuesta de Pokémon para disfrutar, coleccionar y compartir tu pasión por las cartas.

## Detalles
- Producto: Elite Trainer Box Chaos Rising
- Franquicia: Pokémon
- Tipo: cajas
- Idioma: Inglés

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Booster Bundle Mega Evolution', 24990, 'tcg', 'cajas', 'Español', NULL, NULL, 'Pokémon', 'TCG', NULL, 4, 51, 21, 'Dale un impulso a tu colección con Booster Bundle Mega Evolution. Su formato reúne una atractiva propuesta de Pokémon para disfrutar, coleccionar y compartir tu pasión por las cartas.

## Detalles
- Producto: Booster Bundle Mega Evolution
- Franquicia: Pokémon
- Tipo: cajas
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Mega Evolution Ascended Heroes Mega Ex Box', 44990, 'tcg', 'cajas', 'Inglés', NULL, NULL, 'Pokémon', 'TCG', NULL, 5, 88, 28, 'Dale un impulso a tu colección con Mega Evolution Ascended Heroes Mega Ex Box. Su formato reúne una atractiva propuesta de Pokémon para disfrutar, coleccionar y compartir tu pasión por las cartas.

## Detalles
- Producto: Mega Evolution Ascended Heroes Mega Ex Box
- Franquicia: Pokémon
- Tipo: cajas
- Idioma: Inglés

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Booster Box Phantasmal Flames (36 sobres)', 189990, 'tcg', 'cajas', 'Inglés', NULL, NULL, 'Pokémon', 'TCG', NULL, 1, 25, 35, 'Dale un impulso a tu colección con Booster Box Phantasmal Flames (36 sobres). Su formato reúne una atractiva propuesta de Pokémon para disfrutar, coleccionar y compartir tu pasión por las cartas.

## Detalles
- Producto: Booster Box Phantasmal Flames (36 sobres)
- Franquicia: Pokémon
- Tipo: cajas
- Idioma: Inglés

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Lumiose City Mini Tin Collection', 14990, 'tcg', 'latas', 'Español', NULL, NULL, 'Pokémon', 'TCG', NULL, 0, 62, 42, 'Dale un impulso a tu colección con Lumiose City Mini Tin Collection. Su formato reúne una atractiva propuesta de Pokémon para disfrutar, coleccionar y compartir tu pasión por las cartas.

## Detalles
- Producto: Lumiose City Mini Tin Collection
- Franquicia: Pokémon
- Tipo: latas
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Mini Tin Charizard', 12990, 'tcg', 'latas', 'Español', NULL, NULL, 'Pokémon', 'TCG', NULL, 3, 99, 49, 'Dale un impulso a tu colección con Mini Tin Charizard. Su formato reúne una atractiva propuesta de Pokémon para disfrutar, coleccionar y compartir tu pasión por las cartas.

## Detalles
- Producto: Mini Tin Charizard
- Franquicia: Pokémon
- Tipo: latas
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Lata Coleccionable Pikachu', 17990, 'tcg', 'latas', 'Español', NULL, NULL, 'Pokémon', 'TCG', NULL, 4, 36, 56, 'Dale un impulso a tu colección con Lata Coleccionable Pikachu. Su formato reúne una atractiva propuesta de Pokémon para disfrutar, coleccionar y compartir tu pasión por las cartas.

## Detalles
- Producto: Lata Coleccionable Pikachu
- Franquicia: Pokémon
- Tipo: latas
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Ascended Heroes Pin Collection First Partners', 44990, 'tcg', 'latas', 'Español', NULL, NULL, 'Pokémon', 'TCG', NULL, 5, 73, 3, 'Dale un impulso a tu colección con Ascended Heroes Pin Collection First Partners. Su formato reúne una atractiva propuesta de Pokémon para disfrutar, coleccionar y compartir tu pasión por las cartas.

## Detalles
- Producto: Ascended Heroes Pin Collection First Partners
- Franquicia: Pokémon
- Tipo: latas
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Mini Portafolio Pokémon Phantasmal Flames', 10990, 'tcg', 'portafolios', 'Inglés', NULL, NULL, 'Pokémon', 'TCG', NULL, 1, 10, 10, 'Dale un impulso a tu colección con Mini Portafolio Pokémon Phantasmal Flames. Su formato reúne una atractiva propuesta de Pokémon para disfrutar, coleccionar y compartir tu pasión por las cartas.

## Detalles
- Producto: Mini Portafolio Pokémon Phantasmal Flames
- Franquicia: Pokémon
- Tipo: portafolios
- Idioma: Inglés

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Carpeta Pokémon Pikachu 9 bolsillos', 15990, 'tcg', 'portafolios', 'Español', NULL, NULL, 'Pokémon', 'TCG', NULL, 2, 47, 17, 'Dale un impulso a tu colección con Carpeta Pokémon Pikachu 9 bolsillos. Su formato reúne una atractiva propuesta de Pokémon para disfrutar, coleccionar y compartir tu pasión por las cartas.

## Detalles
- Producto: Carpeta Pokémon Pikachu 9 bolsillos
- Franquicia: Pokémon
- Tipo: portafolios
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Álbum Pokémon 360 cartas', 21990, 'tcg', 'portafolios', 'Español', NULL, NULL, 'Pokémon', 'TCG', NULL, 3, 84, 24, 'Dale un impulso a tu colección con Álbum Pokémon 360 cartas. Su formato reúne una atractiva propuesta de Pokémon para disfrutar, coleccionar y compartir tu pasión por las cartas.

## Detalles
- Producto: Álbum Pokémon 360 cartas
- Franquicia: Pokémon
- Tipo: portafolios
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Protectores Standard Pikachu (65 un.)', 4990, 'tcg', 'accesorios', 'Español', NULL, NULL, 'Pokémon', 'TCG', NULL, 4, 21, 31, 'Cuida y organiza tu colección con Protectores Standard Pikachu (65 un.), un accesorio pensado para acompañar tus partidas y mantener tus cartas listas para el próximo duelo.

## Detalles
- Producto: Protectores Standard Pikachu (65 un.)
- Franquicia: Pokémon
- Tipo: accesorios
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Playmat Charizard', 14990, 'tcg', 'accesorios', 'Español', NULL, NULL, 'Pokémon', 'TCG', NULL, 5, 58, 38, 'Cuida y organiza tu colección con Playmat Charizard, un accesorio pensado para acompañar tus partidas y mantener tus cartas listas para el próximo duelo.

## Detalles
- Producto: Playmat Charizard
- Franquicia: Pokémon
- Tipo: accesorios
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Deck Box Eevee', 9990, 'tcg', 'accesorios', 'Español', NULL, NULL, 'Pokémon', 'TCG', NULL, 0, 95, 45, 'Cuida y organiza tu colección con Deck Box Eevee, un accesorio pensado para acompañar tus partidas y mantener tus cartas listas para el próximo duelo.

## Detalles
- Producto: Deck Box Eevee
- Franquicia: Pokémon
- Tipo: accesorios
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Dados y contadores Pokémon', 6990, 'tcg', 'accesorios', 'Español', NULL, NULL, 'Pokémon', 'TCG', NULL, 2, 32, 52, 'Cuida y organiza tu colección con Dados y contadores Pokémon, un accesorio pensado para acompañar tus partidas y mantener tus cartas listas para el próximo duelo.

## Detalles
- Producto: Dados y contadores Pokémon
- Franquicia: Pokémon
- Tipo: accesorios
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Charizard ex (Obsidian Flames)', 12990, 'tcg', 'singles', 'Español', NULL, NULL, 'Pokémon', 'TCG', NULL, 3, 69, 59, 'Una carta individual de Pokémon para sumar una pieza especial a tu colección. Charizard ex (Obsidian Flames) destaca por su identidad y temática, y puede convertirse en la protagonista de tu próxima búsqueda.

## Detalles
- Producto: Charizard ex (Obsidian Flames)
- Franquicia: Pokémon
- Tipo: singles
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Pikachu ex Ilustración Especial', 29990, 'tcg', 'singles', 'Inglés', NULL, NULL, 'Pokémon', 'TCG', NULL, 4, 6, 6, 'Una carta individual de Pokémon para sumar una pieza especial a tu colección. Pikachu ex Ilustración Especial destaca por su identidad y temática, y puede convertirse en la protagonista de tu próxima búsqueda.

## Detalles
- Producto: Pikachu ex Ilustración Especial
- Franquicia: Pokémon
- Tipo: singles
- Idioma: Inglés

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Mewtwo ex', 7990, 'tcg', 'singles', 'Español', NULL, NULL, 'Pokémon', 'TCG', NULL, 5, 43, 13, 'Una carta individual de Pokémon para sumar una pieza especial a tu colección. Mewtwo ex destaca por su identidad y temática, y puede convertirse en la protagonista de tu próxima búsqueda.

## Detalles
- Producto: Mewtwo ex
- Franquicia: Pokémon
- Tipo: singles
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Gardevoir ex', 5990, 'tcg', 'singles', 'Español', NULL, NULL, 'Pokémon', 'TCG', NULL, 1, 80, 20, 'Una carta individual de Pokémon para sumar una pieza especial a tu colección. Gardevoir ex destaca por su identidad y temática, y puede convertirse en la protagonista de tu próxima búsqueda.

## Detalles
- Producto: Gardevoir ex
- Franquicia: Pokémon
- Tipo: singles
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Umbreon ex', 34990, 'tcg', 'singles', 'Inglés', NULL, NULL, 'Pokémon', 'TCG', NULL, 2, 17, 27, 'Una carta individual de Pokémon para sumar una pieza especial a tu colección. Umbreon ex destaca por su identidad y temática, y puede convertirse en la protagonista de tu próxima búsqueda.

## Detalles
- Producto: Umbreon ex
- Franquicia: Pokémon
- Tipo: singles
- Idioma: Inglés

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Lucario ex', 4990, 'tcg', 'singles', 'Español', NULL, NULL, 'Pokémon', 'TCG', NULL, 3, 54, 34, 'Una carta individual de Pokémon para sumar una pieza especial a tu colección. Lucario ex destaca por su identidad y temática, y puede convertirse en la protagonista de tu próxima búsqueda.

## Detalles
- Producto: Lucario ex
- Franquicia: Pokémon
- Tipo: singles
- Idioma: Español

## Ideal para
Fans de Pokémon, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Sobre One Piece Card Game OP-09', 3990, 'tcg', 'sobres', 'Inglés', NULL, NULL, 'One Piece', 'TCG', NULL, 4, 91, 41, 'Abre nuevas posibilidades para tu colección con Sobre One Piece Card Game OP-09. Cada sobre invita a descubrir cartas y disfrutar la emoción de ampliar tu estrategia en One Piece.

## Detalles
- Producto: Sobre One Piece Card Game OP-09
- Franquicia: One Piece
- Tipo: sobres
- Idioma: Inglés

## Ideal para
Fans de One Piece, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Sobre One Piece Card Game OP-10', 3990, 'tcg', 'sobres', 'Español', NULL, NULL, 'One Piece', 'TCG', NULL, 0, 28, 48, 'Abre nuevas posibilidades para tu colección con Sobre One Piece Card Game OP-10. Cada sobre invita a descubrir cartas y disfrutar la emoción de ampliar tu estrategia en One Piece.

## Detalles
- Producto: Sobre One Piece Card Game OP-10
- Franquicia: One Piece
- Tipo: sobres
- Idioma: Español

## Ideal para
Fans de One Piece, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Mazo Inicial One Piece Luffy', 12990, 'tcg', 'mazos', 'Inglés', NULL, NULL, 'One Piece', 'TCG', NULL, 1, 65, 55, 'Prepárate para jugar con Mazo Inicial One Piece Luffy, una opción para quienes quieren llevar su pasión por One Piece directamente a la mesa y disfrutar cada enfrentamiento.

## Detalles
- Producto: Mazo Inicial One Piece Luffy
- Franquicia: One Piece
- Tipo: mazos
- Idioma: Inglés

## Ideal para
Fans de One Piece, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Display One Piece OP-09 (24 sobres)', 79990, 'tcg', 'cajas', 'Inglés', NULL, NULL, 'One Piece', 'TCG', NULL, 2, 2, 2, 'Dale un impulso a tu colección con Display One Piece OP-09 (24 sobres). Su formato reúne una atractiva propuesta de One Piece para disfrutar, coleccionar y compartir tu pasión por las cartas.

## Detalles
- Producto: Display One Piece OP-09 (24 sobres)
- Franquicia: One Piece
- Tipo: cajas
- Idioma: Inglés

## Ideal para
Fans de One Piece, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Sobre Mitos y Leyendas Espada Sagrada', 2990, 'tcg', 'sobres', 'Español', NULL, NULL, 'Mitos y Leyendas', 'TCG', NULL, 3, 39, 9, 'Abre nuevas posibilidades para tu colección con Sobre Mitos y Leyendas Espada Sagrada. Cada sobre invita a descubrir cartas y disfrutar la emoción de ampliar tu estrategia en Mitos y Leyendas.

## Detalles
- Producto: Sobre Mitos y Leyendas Espada Sagrada
- Franquicia: Mitos y Leyendas
- Tipo: sobres
- Idioma: Español

## Ideal para
Fans de Mitos y Leyendas, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Mazo Mitos y Leyendas Helénica', 9990, 'tcg', 'mazos', 'Español', NULL, NULL, 'Mitos y Leyendas', 'TCG', NULL, 4, 76, 16, 'Prepárate para jugar con Mazo Mitos y Leyendas Helénica, una opción para quienes quieren llevar su pasión por Mitos y Leyendas directamente a la mesa y disfrutar cada enfrentamiento.

## Detalles
- Producto: Mazo Mitos y Leyendas Helénica
- Franquicia: Mitos y Leyendas
- Tipo: mazos
- Idioma: Español

## Ideal para
Fans de Mitos y Leyendas, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Sobre Yu-Gi-Oh! Predadores Metálicos', 4990, 'tcg', 'sobres', 'Español', NULL, NULL, 'Yu-Gi-Oh!', 'TCG', NULL, 5, 13, 23, 'Abre nuevas posibilidades para tu colección con Sobre Yu-Gi-Oh! Predadores Metálicos. Cada sobre invita a descubrir cartas y disfrutar la emoción de ampliar tu estrategia en Yu-Gi-Oh!.

## Detalles
- Producto: Sobre Yu-Gi-Oh! Predadores Metálicos
- Franquicia: Yu-Gi-Oh!
- Tipo: sobres
- Idioma: Español

## Ideal para
Fans de Yu-Gi-Oh!, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Sobre Yu-Gi-Oh! Metal Raiders', 4990, 'tcg', 'sobres', 'Inglés', NULL, NULL, 'Yu-Gi-Oh!', 'TCG', NULL, 1, 50, 30, 'Abre nuevas posibilidades para tu colección con Sobre Yu-Gi-Oh! Metal Raiders. Cada sobre invita a descubrir cartas y disfrutar la emoción de ampliar tu estrategia en Yu-Gi-Oh!.

## Detalles
- Producto: Sobre Yu-Gi-Oh! Metal Raiders
- Franquicia: Yu-Gi-Oh!
- Tipo: sobres
- Idioma: Inglés

## Ideal para
Fans de Yu-Gi-Oh!, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Protector Small 62x89mm Wyvern de Sangre (60 un.)', 3990, 'tcg', 'accesorios', 'Español', NULL, NULL, 'Yu-Gi-Oh!', 'TCG', NULL, 2, 87, 37, 'Cuida y organiza tu colección con Protector Small 62x89mm Wyvern de Sangre (60 un.), un accesorio pensado para acompañar tus partidas y mantener tus cartas listas para el próximo duelo.

## Detalles
- Producto: Protector Small 62x89mm Wyvern de Sangre (60 un.)
- Franquicia: Yu-Gi-Oh!
- Tipo: accesorios
- Idioma: Español

## Ideal para
Fans de Yu-Gi-Oh!, coleccionistas y jugadores que buscan una nueva incorporación para su colección o sus partidas.'),
  ('Naruto Uzumaki Figura Articulada', 24990, 'figuras', NULL, NULL, 'Naruto', NULL, 'Naruto', 'Figuras', NULL, 3, 24, 44, 'Lleva tu pasión por Naruto a otro nivel con Naruto Uzumaki Figura Articulada. Una pieza coleccionable que destaca en tu vitrina y suma personalidad a cualquier espacio.

## Detalles
- Producto: Naruto Uzumaki Figura Articulada
- Franquicia: Naruto
- Marca: Naruto

## Ideal para
Coleccionistas y fans que quieren exhibir a Naruto o encontrar un regalo con identidad.'),
  ('Itachi Uchiha Figura Coleccionable', 29990, 'figuras', NULL, NULL, 'Naruto', NULL, 'Naruto', 'Figuras', NULL, 0, 61, 51, 'Lleva tu pasión por Naruto a otro nivel con Itachi Uchiha Figura Coleccionable. Una pieza coleccionable que destaca en tu vitrina y suma personalidad a cualquier espacio.

## Detalles
- Producto: Itachi Uchiha Figura Coleccionable
- Franquicia: Naruto
- Marca: Naruto

## Ideal para
Coleccionistas y fans que quieren exhibir a Naruto o encontrar un regalo con identidad.'),
  ('Kakashi Hatake Figura 18 cm', 22990, 'figuras', NULL, NULL, 'Naruto', NULL, 'Naruto', 'Figuras', NULL, 5, 98, 58, 'Lleva tu pasión por Naruto a otro nivel con Kakashi Hatake Figura 18 cm. Una pieza coleccionable que destaca en tu vitrina y suma personalidad a cualquier espacio.

## Detalles
- Producto: Kakashi Hatake Figura 18 cm
- Franquicia: Naruto
- Marca: Naruto

## Ideal para
Coleccionistas y fans que quieren exhibir a Naruto o encontrar un regalo con identidad.'),
  ('Monkey D. Luffy Gear 5 Figura', 34990, 'figuras', NULL, NULL, 'One Piece', NULL, 'One Piece', 'Figuras', NULL, 1, 35, 5, 'Lleva tu pasión por One Piece a otro nivel con Monkey D. Luffy Gear 5 Figura. Una pieza coleccionable que destaca en tu vitrina y suma personalidad a cualquier espacio.

## Detalles
- Producto: Monkey D. Luffy Gear 5 Figura
- Franquicia: One Piece
- Marca: One Piece

## Ideal para
Coleccionistas y fans que quieren exhibir a One Piece o encontrar un regalo con identidad.'),
  ('Roronoa Zoro Figura Coleccionable', 31990, 'figuras', NULL, NULL, 'One Piece', NULL, 'One Piece', 'Figuras', NULL, 2, 72, 12, 'Lleva tu pasión por One Piece a otro nivel con Roronoa Zoro Figura Coleccionable. Una pieza coleccionable que destaca en tu vitrina y suma personalidad a cualquier espacio.

## Detalles
- Producto: Roronoa Zoro Figura Coleccionable
- Franquicia: One Piece
- Marca: One Piece

## Ideal para
Coleccionistas y fans que quieren exhibir a One Piece o encontrar un regalo con identidad.'),
  ('Trafalgar Law Figura 20 cm', 27990, 'figuras', NULL, NULL, 'One Piece', NULL, 'One Piece', 'Figuras', NULL, 3, 9, 19, 'Lleva tu pasión por One Piece a otro nivel con Trafalgar Law Figura 20 cm. Una pieza coleccionable que destaca en tu vitrina y suma personalidad a cualquier espacio.

## Detalles
- Producto: Trafalgar Law Figura 20 cm
- Franquicia: One Piece
- Marca: One Piece

## Ideal para
Coleccionistas y fans que quieren exhibir a One Piece o encontrar un regalo con identidad.'),
  ('Peluche Pikachu 30 cm', 16990, 'figuras', NULL, NULL, 'Pokémon', NULL, 'Pokémon', 'Figuras', NULL, 4, 46, 26, 'Lleva tu pasión por Pokémon a otro nivel con Peluche Pikachu 30 cm. Una pieza coleccionable que destaca en tu vitrina y suma personalidad a cualquier espacio.

## Detalles
- Producto: Peluche Pikachu 30 cm
- Franquicia: Pokémon
- Marca: Pokémon

## Ideal para
Coleccionistas y fans que quieren exhibir a Pokémon o encontrar un regalo con identidad.'),
  ('Figura Charizard Articulada', 19990, 'figuras', NULL, NULL, 'Pokémon', NULL, 'Pokémon', 'Figuras', NULL, 5, 83, 33, 'Lleva tu pasión por Pokémon a otro nivel con Figura Charizard Articulada. Una pieza coleccionable que destaca en tu vitrina y suma personalidad a cualquier espacio.

## Detalles
- Producto: Figura Charizard Articulada
- Franquicia: Pokémon
- Marca: Pokémon

## Ideal para
Coleccionistas y fans que quieren exhibir a Pokémon o encontrar un regalo con identidad.'),
  ('Figura Eevee y Evoluciones', 24990, 'figuras', NULL, NULL, 'Pokémon', NULL, 'Pokémon', 'Figuras', NULL, 1, 20, 40, 'Lleva tu pasión por Pokémon a otro nivel con Figura Eevee y Evoluciones. Una pieza coleccionable que destaca en tu vitrina y suma personalidad a cualquier espacio.

## Detalles
- Producto: Figura Eevee y Evoluciones
- Franquicia: Pokémon
- Marca: Pokémon

## Ideal para
Coleccionistas y fans que quieren exhibir a Pokémon o encontrar un regalo con identidad.'),
  ('Goku Ultra Instinto Figura', 28990, 'figuras', NULL, NULL, 'Dragon Ball', NULL, 'Dragon Ball', 'Figuras', NULL, 2, 57, 47, 'Lleva tu pasión por Dragon Ball a otro nivel con Goku Ultra Instinto Figura. Una pieza coleccionable que destaca en tu vitrina y suma personalidad a cualquier espacio.

## Detalles
- Producto: Goku Ultra Instinto Figura
- Franquicia: Dragon Ball
- Marca: Dragon Ball

## Ideal para
Coleccionistas y fans que quieren exhibir a Dragon Ball o encontrar un regalo con identidad.'),
  ('Vegeta Super Saiyan Figura', 26990, 'figuras', NULL, NULL, 'Dragon Ball', NULL, 'Dragon Ball', 'Figuras', NULL, 0, 94, 54, 'Lleva tu pasión por Dragon Ball a otro nivel con Vegeta Super Saiyan Figura. Una pieza coleccionable que destaca en tu vitrina y suma personalidad a cualquier espacio.

## Detalles
- Producto: Vegeta Super Saiyan Figura
- Franquicia: Dragon Ball
- Marca: Dragon Ball

## Ideal para
Coleccionistas y fans que quieren exhibir a Dragon Ball o encontrar un regalo con identidad.'),
  ('Tanjiro Kamado Figura', 25990, 'figuras', NULL, NULL, 'Demon Slayer', NULL, 'Demon Slayer', 'Figuras', NULL, 4, 31, 1, 'Lleva tu pasión por Demon Slayer a otro nivel con Tanjiro Kamado Figura. Una pieza coleccionable que destaca en tu vitrina y suma personalidad a cualquier espacio.

## Detalles
- Producto: Tanjiro Kamado Figura
- Franquicia: Demon Slayer
- Marca: Demon Slayer

## Ideal para
Coleccionistas y fans que quieren exhibir a Demon Slayer o encontrar un regalo con identidad.'),
  ('Funko Pop Hisoka Hunter x Hunter', 18990, 'figuras', NULL, NULL, 'Funko Pop', NULL, 'Hunter x Hunter', 'Figuras', NULL, 5, 68, 8, 'Lleva tu pasión por Hunter x Hunter a otro nivel con Funko Pop Hisoka Hunter x Hunter. Una pieza coleccionable que destaca en tu vitrina y suma personalidad a cualquier espacio.

## Detalles
- Producto: Funko Pop Hisoka Hunter x Hunter
- Franquicia: Hunter x Hunter
- Marca: Funko Pop

## Ideal para
Coleccionistas y fans que quieren exhibir a Hunter x Hunter o encontrar un regalo con identidad.'),
  ('Funko Pop Pikachu', 16990, 'figuras', NULL, NULL, 'Funko Pop', NULL, 'Pokémon', 'Figuras', NULL, 1, 5, 15, 'Lleva tu pasión por Pokémon a otro nivel con Funko Pop Pikachu. Una pieza coleccionable que destaca en tu vitrina y suma personalidad a cualquier espacio.

## Detalles
- Producto: Funko Pop Pikachu
- Franquicia: Pokémon
- Marca: Funko Pop

## Ideal para
Coleccionistas y fans que quieren exhibir a Pokémon o encontrar un regalo con identidad.'),
  ('God of War Ragnarök', 39990, 'venta-videojuegos', NULL, NULL, NULL, 'PlayStation 5', NULL, 'Videojuegos', NULL, 2, 42, 22, 'Entra de lleno en la aventura de God of War Ragnarök y disfruta tus partidas en PlayStation 5. Una gran elección para sumar una nueva experiencia a tu colección de videojuegos.

## Detalles
- Producto: God of War Ragnarök
- Plataforma: PlayStation 5

## Ideal para
Jugadores que buscan una nueva aventura, competir o disfrutar de una buena sesión de juego en PlayStation 5.'),
  ('Spider-Man 2', 44990, 'venta-videojuegos', NULL, NULL, NULL, 'PlayStation 5', NULL, 'Videojuegos', NULL, 3, 79, 29, 'Entra de lleno en la aventura de Spider-Man 2 y disfruta tus partidas en PlayStation 5. Una gran elección para sumar una nueva experiencia a tu colección de videojuegos.

## Detalles
- Producto: Spider-Man 2
- Plataforma: PlayStation 5

## Ideal para
Jugadores que buscan una nueva aventura, competir o disfrutar de una buena sesión de juego en PlayStation 5.'),
  ('EA Sports FC 26', 54990, 'venta-videojuegos', NULL, NULL, NULL, 'PlayStation 5', NULL, 'Videojuegos', NULL, 4, 16, 36, 'Entra de lleno en la aventura de EA Sports FC 26 y disfruta tus partidas en PlayStation 5. Una gran elección para sumar una nueva experiencia a tu colección de videojuegos.

## Detalles
- Producto: EA Sports FC 26
- Plataforma: PlayStation 5

## Ideal para
Jugadores que buscan una nueva aventura, competir o disfrutar de una buena sesión de juego en PlayStation 5.'),
  ('Astro Bot', 49990, 'venta-videojuegos', NULL, NULL, NULL, 'PlayStation 5', NULL, 'Videojuegos', NULL, 5, 53, 43, 'Entra de lleno en la aventura de Astro Bot y disfruta tus partidas en PlayStation 5. Una gran elección para sumar una nueva experiencia a tu colección de videojuegos.

## Detalles
- Producto: Astro Bot
- Plataforma: PlayStation 5

## Ideal para
Jugadores que buscan una nueva aventura, competir o disfrutar de una buena sesión de juego en PlayStation 5.'),
  ('The Last of Us Part II', 24990, 'venta-videojuegos', NULL, NULL, NULL, 'PlayStation 4', NULL, 'Videojuegos', NULL, 1, 90, 50, 'Entra de lleno en la aventura de The Last of Us Part II y disfruta tus partidas en PlayStation 4. Una gran elección para sumar una nueva experiencia a tu colección de videojuegos.

## Detalles
- Producto: The Last of Us Part II
- Plataforma: PlayStation 4

## Ideal para
Jugadores que buscan una nueva aventura, competir o disfrutar de una buena sesión de juego en PlayStation 4.'),
  ('Ghost of Tsushima', 24990, 'venta-videojuegos', NULL, NULL, NULL, 'PlayStation 4', NULL, 'Videojuegos', NULL, 0, 27, 57, 'Entra de lleno en la aventura de Ghost of Tsushima y disfruta tus partidas en PlayStation 4. Una gran elección para sumar una nueva experiencia a tu colección de videojuegos.

## Detalles
- Producto: Ghost of Tsushima
- Plataforma: PlayStation 4

## Ideal para
Jugadores que buscan una nueva aventura, competir o disfrutar de una buena sesión de juego en PlayStation 4.'),
  ('Mario Kart World', 69990, 'venta-videojuegos', NULL, NULL, NULL, 'Nintendo Switch 2', NULL, 'Videojuegos', NULL, 3, 64, 4, 'Entra de lleno en la aventura de Mario Kart World y disfruta tus partidas en Nintendo Switch 2. Una gran elección para sumar una nueva experiencia a tu colección de videojuegos.

## Detalles
- Producto: Mario Kart World
- Plataforma: Nintendo Switch 2

## Ideal para
Jugadores que buscan una nueva aventura, competir o disfrutar de una buena sesión de juego en Nintendo Switch 2.'),
  ('Pokémon Legends: Z-A', 59990, 'venta-videojuegos', NULL, NULL, NULL, 'Nintendo Switch', 'Pokémon', 'Videojuegos', NULL, 4, 1, 11, 'Entra de lleno en la aventura de Pokémon Legends: Z-A y disfruta tus partidas en Nintendo Switch. Una gran elección para sumar una nueva experiencia a tu colección de videojuegos.

## Detalles
- Producto: Pokémon Legends: Z-A
- Franquicia: Pokémon
- Plataforma: Nintendo Switch

## Ideal para
Jugadores que buscan una nueva aventura, competir o disfrutar de una buena sesión de juego en Nintendo Switch.'),
  ('The Legend of Zelda: Tears of the Kingdom', 59990, 'venta-videojuegos', NULL, NULL, NULL, 'Nintendo Switch', NULL, 'Videojuegos', NULL, 5, 38, 18, 'Entra de lleno en la aventura de The Legend of Zelda: Tears of the Kingdom y disfruta tus partidas en Nintendo Switch. Una gran elección para sumar una nueva experiencia a tu colección de videojuegos.

## Detalles
- Producto: The Legend of Zelda: Tears of the Kingdom
- Plataforma: Nintendo Switch

## Ideal para
Jugadores que buscan una nueva aventura, competir o disfrutar de una buena sesión de juego en Nintendo Switch.'),
  ('Super Mario Bros. Wonder', 54990, 'venta-videojuegos', NULL, NULL, NULL, 'Nintendo Switch', NULL, 'Videojuegos', NULL, 1, 75, 25, 'Entra de lleno en la aventura de Super Mario Bros. Wonder y disfruta tus partidas en Nintendo Switch. Una gran elección para sumar una nueva experiencia a tu colección de videojuegos.

## Detalles
- Producto: Super Mario Bros. Wonder
- Plataforma: Nintendo Switch

## Ideal para
Jugadores que buscan una nueva aventura, competir o disfrutar de una buena sesión de juego en Nintendo Switch.'),
  ('Forza Horizon 5', 39990, 'venta-videojuegos', NULL, NULL, NULL, 'Xbox Series X|S', NULL, 'Videojuegos', NULL, 2, 12, 32, 'Entra de lleno en la aventura de Forza Horizon 5 y disfruta tus partidas en Xbox Series X|S. Una gran elección para sumar una nueva experiencia a tu colección de videojuegos.

## Detalles
- Producto: Forza Horizon 5
- Plataforma: Xbox Series X|S

## Ideal para
Jugadores que buscan una nueva aventura, competir o disfrutar de una buena sesión de juego en Xbox Series X|S.'),
  ('Halo Infinite', 29990, 'venta-videojuegos', NULL, NULL, NULL, 'Xbox Series X|S', NULL, 'Videojuegos', NULL, 3, 49, 39, 'Entra de lleno en la aventura de Halo Infinite y disfruta tus partidas en Xbox Series X|S. Una gran elección para sumar una nueva experiencia a tu colección de videojuegos.

## Detalles
- Producto: Halo Infinite
- Plataforma: Xbox Series X|S

## Ideal para
Jugadores que buscan una nueva aventura, competir o disfrutar de una buena sesión de juego en Xbox Series X|S.'),
  ('PlayStation 5 Slim 1 TB', 599990, 'consolas', 'Venta', NULL, NULL, 'PlayStation 5', NULL, 'Consolas', NULL, 4, 86, 46, 'Lleva tu experiencia de juego a casa con PlayStation 5 Slim 1 TB. Disfruta tus títulos favoritos en PlayStation 5 y convierte cada sesión en el comienzo de una nueva aventura.

## Detalles
- Producto: PlayStation 5 Slim 1 TB
- Tipo: Venta
- Plataforma: PlayStation 5

## Ideal para
Quienes quieren renovar su equipo, comenzar a jugar o encontrar una gran opción para compartir en casa.'),
  ('Nintendo Switch OLED', 349990, 'consolas', 'Venta', NULL, NULL, 'Nintendo Switch', NULL, 'Consolas', NULL, 5, 23, 53, 'Lleva tu experiencia de juego a casa con Nintendo Switch OLED. Disfruta tus títulos favoritos en Nintendo Switch y convierte cada sesión en el comienzo de una nueva aventura.

## Detalles
- Producto: Nintendo Switch OLED
- Tipo: Venta
- Plataforma: Nintendo Switch

## Ideal para
Quienes quieren renovar su equipo, comenzar a jugar o encontrar una gran opción para compartir en casa.'),
  ('Xbox Series S 512 GB', 299990, 'consolas', 'Venta', NULL, NULL, 'Xbox Series X|S', NULL, 'Consolas', NULL, 0, 60, 0, 'Lleva tu experiencia de juego a casa con Xbox Series S 512 GB. Disfruta tus títulos favoritos en Xbox Series X|S y convierte cada sesión en el comienzo de una nueva aventura.

## Detalles
- Producto: Xbox Series S 512 GB
- Tipo: Venta
- Plataforma: Xbox Series X|S

## Ideal para
Quienes quieren renovar su equipo, comenzar a jugar o encontrar una gran opción para compartir en casa.'),
  ('PlayStation 4 Slim 500 GB (usada)', 149990, 'consolas', 'Usadas', NULL, NULL, 'PlayStation 4', NULL, 'Consolas', NULL, 2, 97, 7, 'Haz realidad tus próximas partidas con PlayStation 4 Slim 500 GB (usada). Una alternativa más accesible para disfrutar el catálogo de PlayStation 4 y dar el siguiente paso en tu espacio gamer.

## Detalles
- Producto: PlayStation 4 Slim 500 GB (usada)
- Tipo: Usadas
- Plataforma: PlayStation 4

## Ideal para
Jugadores que buscan una consola para disfrutar en casa y sumar nuevas experiencias a su colección.'),
  ('Nintendo Switch Lite (usada)', 129990, 'consolas', 'Usadas', NULL, NULL, 'Nintendo Switch', NULL, 'Consolas', NULL, 3, 34, 14, 'Haz realidad tus próximas partidas con Nintendo Switch Lite (usada). Una alternativa más accesible para disfrutar el catálogo de Nintendo Switch y dar el siguiente paso en tu espacio gamer.

## Detalles
- Producto: Nintendo Switch Lite (usada)
- Tipo: Usadas
- Plataforma: Nintendo Switch

## Ideal para
Jugadores que buscan una consola para disfrutar en casa y sumar nuevas experiencias a su colección.'),
  ('Arriendo PlayStation 5 por día', 15990, 'consolas', 'Arriendo', NULL, NULL, 'PlayStation 5', NULL, 'Consolas', NULL, 4, 71, 21, 'Disfruta PlayStation 5 sin tener que comprar una consola. Arriendo PlayStation 5 por día está disponible en modalidad de arriendo por día para que organices una sesión de juego, una junta o un fin de semana especial.

## Detalles
- Producto: Arriendo PlayStation 5 por día
- Tipo: Arriendo
- Plataforma: PlayStation 5

## Ideal para
Quienes quieren jugar por un periodo acotado, probar una consola o compartir videojuegos con amigos y familia.'),
  ('Arriendo Nintendo Switch por día', 9990, 'consolas', 'Arriendo', NULL, NULL, 'Nintendo Switch', NULL, 'Consolas', NULL, 5, 8, 28, 'Disfruta Nintendo Switch sin tener que comprar una consola. Arriendo Nintendo Switch por día está disponible en modalidad de arriendo por día para que organices una sesión de juego, una junta o un fin de semana especial.

## Detalles
- Producto: Arriendo Nintendo Switch por día
- Tipo: Arriendo
- Plataforma: Nintendo Switch

## Ideal para
Quienes quieren jugar por un periodo acotado, probar una consola o compartir videojuegos con amigos y familia.'),
  ('Control DualSense PS5 Blanco', 69990, 'accesorios-consolas', 'Controles', NULL, NULL, 'PlayStation 5', NULL, 'Accesorios', NULL, 1, 45, 35, 'Toma el control de tus partidas con Control DualSense PS5 Blanco. Un accesorio ideal para completar tu setup y disfrutar tus juegos en PlayStation 5.

## Detalles
- Producto: Control DualSense PS5 Blanco
- Tipo: Controles
- Plataforma: PlayStation 5

## Ideal para
Gamers que quieren completar, cuidar o mejorar su setup con un accesorio para PlayStation 5.'),
  ('Control Pro Nintendo Switch', 64990, 'accesorios-consolas', 'Controles', NULL, NULL, 'Nintendo Switch', NULL, 'Accesorios', NULL, 2, 82, 42, 'Toma el control de tus partidas con Control Pro Nintendo Switch. Un accesorio ideal para completar tu setup y disfrutar tus juegos en Nintendo Switch.

## Detalles
- Producto: Control Pro Nintendo Switch
- Tipo: Controles
- Plataforma: Nintendo Switch

## Ideal para
Gamers que quieren completar, cuidar o mejorar su setup con un accesorio para Nintendo Switch.'),
  ('Control Xbox Series Negro', 64990, 'accesorios-consolas', 'Controles', NULL, NULL, 'Xbox Series X|S', NULL, 'Accesorios', NULL, 3, 19, 49, 'Toma el control de tus partidas con Control Xbox Series Negro. Un accesorio ideal para completar tu setup y disfrutar tus juegos en Xbox Series X|S.

## Detalles
- Producto: Control Xbox Series Negro
- Tipo: Controles
- Plataforma: Xbox Series X|S

## Ideal para
Gamers que quieren completar, cuidar o mejorar su setup con un accesorio para Xbox Series X|S.'),
  ('Audífonos Pulse 3D PS5', 99990, 'accesorios-consolas', 'Audífonos', NULL, NULL, 'PlayStation 5', NULL, 'Accesorios', NULL, 4, 56, 56, 'Sumérgete en cada partida con Audífonos Pulse 3D PS5. Completa tu espacio gamer y disfruta tus sesiones de juego con un accesorio pensado para acompañarte en PlayStation 5.

## Detalles
- Producto: Audífonos Pulse 3D PS5
- Tipo: Audífonos
- Plataforma: PlayStation 5

## Ideal para
Gamers que quieren completar, cuidar o mejorar su setup con un accesorio para PlayStation 5.'),
  ('Audífonos Gamer Multiplataforma', 29990, 'accesorios-consolas', 'Audífonos', NULL, NULL, 'Multiplataforma', NULL, 'Accesorios', NULL, 0, 93, 3, 'Sumérgete en cada partida con Audífonos Gamer Multiplataforma. Completa tu espacio gamer y disfruta tus sesiones de juego con un accesorio pensado para acompañarte en Multiplataforma.

## Detalles
- Producto: Audífonos Gamer Multiplataforma
- Tipo: Audífonos
- Plataforma: Multiplataforma

## Ideal para
Gamers que quieren completar, cuidar o mejorar su setup con un accesorio para Multiplataforma.'),
  ('Funda de silicona para control PS5', 7990, 'accesorios-consolas', 'Fundas y protección', NULL, NULL, 'PlayStation 5', NULL, 'Accesorios', NULL, 1, 30, 10, 'Protege y transporta tu equipo con Funda de silicona para control PS5. Una forma práctica de cuidar tus accesorios y mantener tu setup listo para la próxima partida.

## Detalles
- Producto: Funda de silicona para control PS5
- Tipo: Fundas y protección
- Plataforma: PlayStation 5

## Ideal para
Gamers que quieren completar, cuidar o mejorar su setup con un accesorio para PlayStation 5.'),
  ('Estuche rígido Nintendo Switch', 12990, 'accesorios-consolas', 'Fundas y protección', NULL, NULL, 'Nintendo Switch', NULL, 'Accesorios', NULL, 2, 67, 17, 'Protege y transporta tu equipo con Estuche rígido Nintendo Switch. Una forma práctica de cuidar tus accesorios y mantener tu setup listo para la próxima partida.

## Detalles
- Producto: Estuche rígido Nintendo Switch
- Tipo: Fundas y protección
- Plataforma: Nintendo Switch

## Ideal para
Gamers que quieren completar, cuidar o mejorar su setup con un accesorio para Nintendo Switch.'),
  ('Cargador doble para controles PS5', 19990, 'accesorios-consolas', 'Cargadores y cables', NULL, NULL, 'PlayStation 5', NULL, 'Accesorios', NULL, 3, 4, 24, 'Mantén tu setup preparado con Cargador doble para controles PS5. Un accesorio práctico para acompañar tus sesiones de juego y tener tu equipo listo cuando comienza la partida.

## Detalles
- Producto: Cargador doble para controles PS5
- Tipo: Cargadores y cables
- Plataforma: PlayStation 5

## Ideal para
Gamers que quieren completar, cuidar o mejorar su setup con un accesorio para PlayStation 5.'),
  ('Cable USB-C carga rápida 2 m', 5990, 'accesorios-consolas', 'Cargadores y cables', NULL, NULL, 'Multiplataforma', NULL, 'Accesorios', NULL, 4, 41, 31, 'Mantén tu setup preparado con Cable USB-C carga rápida 2 m. Un accesorio práctico para acompañar tus sesiones de juego y tener tu equipo listo cuando comienza la partida.

## Detalles
- Producto: Cable USB-C carga rápida 2 m
- Tipo: Cargadores y cables
- Plataforma: Multiplataforma

## Ideal para
Gamers que quieren completar, cuidar o mejorar su setup con un accesorio para Multiplataforma.')
;

-- Correcciones respecto del front:
--  * Códigos de barras: el front genera 8 a 12 dígitos sin verificador; la API exige EAN-8/UPC-A/EAN-13 válido.
--    Aquí se usan EAN-13 de uso interno (prefijo GS1 200) con dígito verificador.
--  * Singles y arriendos quedan sin código de barras (no se escanean).
--  * fecha_ingreso relativa a hoy (el front usa una fecha fija).
INSERT INTO producto (nombre, descripcion, precio, categoria_id, tipo, idioma, marca, plataforma, franquicia_id, linea,
                      codigo_barras, limite_por_cliente, stock, popularidad, fecha_ingreso, publicado,
                      creado_en, actualizado_en)
SELECT d.nombre, d.descripcion, d.precio, c.id, d.tipo, d.idioma, d.marca, d.plataforma, f.id, d.linea,
       CASE WHEN d.tipo IN ('Arriendo', 'singles') THEN NULL
            ELSE pg_temp.ean13('200' || lpad(d.n::text, 9, '0')) END,
       d.limite, d.stock, d.popularidad,
       pg_temp.hoy() - round(d.dias * 1.5)::int,
       d.nombre NOT IN ('Booster Box Phantasmal Flames (36 sobres)', 'Arriendo Nintendo Switch por día'),
       pg_temp.ts(pg_temp.hoy() - round(d.dias * 1.5)::int, 9),
       pg_temp.ts(pg_temp.hoy() - round(d.dias * 1.5)::int, 9)
FROM demo_producto d
JOIN categoria c ON c.slug = d.categoria
LEFT JOIN franquicia f ON f.nombre = d.franquicia
ORDER BY d.n;

-- Producto descontinuado (borrado lógico): conserva su historial de ventas.
UPDATE producto SET eliminado_en = pg_temp.ts(pg_temp.hoy() - 5, 18), actualizado_en = pg_temp.ts(pg_temp.hoy() - 5, 18)
 WHERE nombre = 'Mini Tin Charizard';

-- Productos extra para pruebas de carga (solo con escala > 1): (escala − 1) copias del catálogo, publicadas, sin
-- límite por cliente y con código de barras propio (EAN-13 interno con prefijo 201, distinto del 200 de arriba).
INSERT INTO producto (nombre, descripcion, precio, categoria_id, tipo, idioma, marca, plataforma, franquicia_id, linea,
                      codigo_barras, limite_por_cliente, stock, popularidad, fecha_ingreso, publicado,
                      creado_en, actualizado_en)
SELECT d.nombre || ' · Lote ' || (g + 1), d.descripcion,
       d.precio + (g % 5) * 500, c.id, d.tipo, d.idioma, d.marca, d.plataforma, f.id, d.linea,
       CASE WHEN d.tipo IN ('Arriendo', 'singles') THEN NULL
            ELSE pg_temp.ean13('201' || lpad(g::text, 3, '0') || lpad(d.n::text, 6, '0')) END,
       NULL,
       floor(pg_temp.rnd('ls' || g || '-' || d.n) * 40)::int,
       floor(pg_temp.rnd('lp' || g || '-' || d.n) * 100)::int,
       pg_temp.hoy() - (10 + floor(pg_temp.rnd('ld' || g || '-' || d.n) * 120)::int),
       TRUE,
       pg_temp.ts(pg_temp.hoy() - 130, 9), pg_temp.ts(pg_temp.hoy() - 130, 9)
FROM demo_producto d
CROSS JOIN generate_series(1, current_setting('demo.escala')::int - 1) AS g
JOIN categoria c ON c.slug = d.categoria
LEFT JOIN franquicia f ON f.nombre = d.franquicia
ORDER BY g, d.n;

-- Imágenes: portada para todos; galería de 3 para cajas y consolas (URLs https externas de ejemplo).
INSERT INTO producto_imagen (producto_id, posicion, url_externa, es_portada)
SELECT p.id, 0, 'https://placehold.co/800x800/png?text=' || replace(coalesce(p.linea, 'Primer Player'), ' ', '+'), TRUE
FROM producto p;

INSERT INTO producto_imagen (producto_id, posicion, url_externa, es_portada)
SELECT p.id, g, 'https://placehold.co/800x800/png?text=' || replace(coalesce(p.linea, 'Primer Player'), ' ', '+') || '+' || (g + 1), FALSE
FROM producto p CROSS JOIN generate_series(1, 2) AS g
WHERE p.tipo IN ('cajas', 'Venta');

-- =====================================================================================================
-- 4. Eventos (los 3 del front + casos de prueba). Lugar: la tienda.
-- =====================================================================================================
INSERT INTO evento (titulo, tipo, fecha_hora, fecha_hora_fin, lugar, precio, cupos_totales, limite_por_cliente,
                    descripcion, consideraciones, publicado, creado_en, actualizado_en, eliminado_en)
SELECT v.titulo, v.tipo,
       pg_temp.ts(pg_temp.hoy() + v.dia, v.hora),
       CASE WHEN v.duracion IS NOT NULL THEN pg_temp.ts(pg_temp.hoy() + v.dia, v.hora + v.duracion) END,
       'Av. Portales Ote. 1126, San Bernardo', v.precio, v.cupos, v.limite, v.descripcion, v.consideraciones,
       v.publicado,
       pg_temp.ts(pg_temp.hoy() + least(v.dia, 0) - 35, 11),
       pg_temp.ts(pg_temp.hoy() + least(v.dia, 0) - 30, 11),
       CASE WHEN v.eliminado THEN now() - interval '3 days' END
FROM (VALUES
  ('League Challenge Pokémon TCG', 'league-challenge', 2, 15.0, NULL::float8, 5000::bigint, 16, NULL::int,
   'Pon a prueba tu mazo en una tarde de estrategia y Pokémon TCG junto a la comunidad.',
   ARRAY['Trae tu mazo preparado para jugar.', 'Confirma inscripción y cupos disponibles en tienda.',
         'Llega con anticipación para revisar las indicaciones del torneo.'], TRUE, FALSE),
  ('Prelanzamiento nueva expansión Pokémon', 'prelanzamiento', 9, 16.0, NULL, 25000, 12, 2,
   'Descubre la nueva expansión antes de su lanzamiento y juega con las cartas que abras.',
   ARRAY['No necesitas llevar un mazo armado.', 'Consulta en tienda qué producto incluye la inscripción.',
         'Máximo 2 cupos por persona.'], TRUE, FALSE),
  ('League Cup Pokémon TCG', 'league-cup', 23, 14.0, NULL, 7000, 20, NULL,
   'Cierra el año compitiendo en una jornada especial de Pokémon TCG y estrategia.',
   ARRAY['Prepara tu mazo y revisa la lista antes del torneo.', 'Consulta en tienda los requisitos de participación.',
         'Inscripción, cupos y premios sujetos a confirmación.'], TRUE, FALSE),
  ('Torneo Yu-Gi-Oh! Edison Format', 'torneo-tcg', 6, 17.0, NULL, 4000, 24, 1,
   'Torneo suizo de Yu-Gi-Oh! con la lista Edison. Premios en sobres para el top 4.',
   ARRAY['Mazo de 40 a 60 cartas con lista Edison.', 'Un cupo por persona.'], TRUE, FALSE),
  ('Lanzamiento One Piece OP-11', 'lanzamiento', 16, 12.0, 6.0, 0, 30, NULL,
   'Celebra el lanzamiento de la nueva edición de One Piece Card Game. Entrada liberada con inscripción.',
   ARRAY['Entrada gratuita, cupos limitados.', 'Habrá venta de sobres y displays durante la jornada.'], TRUE, FALSE),
  ('Noche de Mario Kart World', 'videojuegos', 12, 19.0, 3.0, 3000, 16, NULL,
   'Torneo casual de Mario Kart World en Nintendo Switch 2 con pantalla gigante.',
   ARRAY['Los controles los pone la tienda.', 'Formato por eliminación en grupos de 4.'], TRUE, FALSE),
  ('Taller de pintura de figuras', 'otros', 30, 11.0, 3.0, 12000, 10, NULL,
   'Aprende técnicas básicas para pintar y personalizar figuras coleccionables.',
   ARRAY['Materiales incluidos.', 'Recomendado desde 12 años.'], FALSE, FALSE),   -- borrador (no publicado)
  ('Torneo Mitos y Leyendas Primer Bloque', 'torneo-tcg', 18, 15.0, NULL, 3000, 16, NULL,
   'Torneo de Mitos y Leyendas formato Primer Bloque.',
   ARRAY['Trae tu mazo y tu lista impresa.'], TRUE, TRUE),                        -- eliminado
  ('League Challenge Pokémon TCG de apertura', 'league-challenge', -25, 15.0, NULL, 5000, 16, NULL,
   'Primera League Challenge de la temporada en Primer Player.',
   ARRAY['Trae tu mazo preparado para jugar.'], TRUE, FALSE),                     -- pasado
  ('Prelanzamiento Mega Evolution', 'prelanzamiento', -40, 16.0, NULL, 25000, 12, 2,
   'Prelanzamiento de Mega Evolution con kit de prelanzamiento incluido.',
   ARRAY['Incluye kit de prelanzamiento.', 'Máximo 2 cupos por persona.'], TRUE, FALSE)  -- pasado y lleno
) AS v(titulo, tipo, dia, hora, duracion, precio, cupos, limite, descripcion, consideraciones, publicado, eliminado);

-- =====================================================================================================
-- 5. Solicitudes de cumpleaños (las aprobadas crean su evento "tienda cerrada")
-- =====================================================================================================
DO $$
DECLARE
  v_hoy   date   := pg_temp.hoy();
  v_vend  bigint := pg_temp.uid('vendedor@primerplayer.cl');
  v_admin bigint := pg_temp.uid('admin@primerplayer.cl');
  s record; v_ev bigint; v_ini timestamptz; v_fin timestamptz; v_creada timestamptz; v_resuelta timestamptz;
BEGIN
  FOR s IN SELECT * FROM (VALUES
    ('cliente@primerplayer.cl',                8,   15.0, 3, 'Tomás (8 años)',    15, '+56 9 1234 5678', 'Temática Pokémon. ¿Podemos llevar torta?', 'PENDIENTE', NULL::text, 2),
    ('valentina.perez@demo.primerplayer.cl',   14,  16.0, 3, 'Valentina',         25, '+56 9 7311 2045', NULL,                                       'PENDIENTE', NULL, 1),
    ('benjamin.gonzalez@demo.primerplayer.cl', 26,  12.0, 3, 'Benja (10 años)',   12, '+56 9 6001 2233', 'Nos gustaría un mini torneo de Mario Kart.', 'PENDIENTE', NULL, 0),
    ('isidora.fuentes@demo.primerplayer.cl',   5,   15.0, 3, 'Isidora (12 años)', 20, '+56 9 5402 7731', NULL,                                       'APROBADA',  NULL, 6),
    ('florencia.castro@demo.primerplayer.cl',  19,  16.0, 4, 'Martín (9 años)',   30, '+56 9 4471 9902', 'Somos unos 30 niños.',                     'APROBADA',  NULL, 4),
    ('cliente@primerplayer.cl',                -10, 15.0, 3, 'Sofía (7 años)',    10, '+56 9 1234 5678', NULL,                                       'APROBADA',  NULL, 18),
    ('tomas.silva@demo.primerplayer.cl',       9,   15.0, 3, 'Tomás Silva',       18, '+56 9 9183 4420', NULL,                                       'RECHAZADA',
     'Ese día hay un prelanzamiento en la tienda. Te proponemos otra fecha.', 3),
    ('camila.rojas@demo.primerplayer.cl',      21,  17.0, 2, 'Camila',            8,  '+56 9 8765 4321', NULL,                                       'CANCELADA', NULL, 7)
  ) AS v(email, dia, hora, duracion, festejado, invitados, telefono, comentario, estado, motivo, creada_hace) LOOP
    v_ini      := pg_temp.ts(v_hoy + s.dia, s.hora);
    v_fin      := v_ini + make_interval(hours => s.duracion);
    v_creada   := least(now() - make_interval(days => s.creada_hace), v_ini - interval '3 days');
    v_resuelta := least(v_creada + interval '1 day', now());
    v_ev       := NULL;

    IF s.estado = 'APROBADA' THEN
      INSERT INTO evento (titulo, tipo, fecha_hora, fecha_hora_fin, lugar, precio, cupos_totales, descripcion,
                          publicado, creado_en, actualizado_en)
      VALUES ('Cumpleaños privado · tienda cerrada', 'cumpleanos', v_ini, v_fin, 'Av. Portales Ote. 1126, San Bernardo', 0, NULL,
              format('La tienda estará cerrada al público de %s a %s hrs por un evento privado.',
                     to_char(v_ini AT TIME ZONE 'America/Santiago', 'HH24:MI'),
                     to_char(v_fin AT TIME ZONE 'America/Santiago', 'HH24:MI')),
              TRUE, v_resuelta, v_resuelta)
      RETURNING id INTO v_ev;
    END IF;

    INSERT INTO solicitud_cumpleanos (cliente_id, inicio, fin, festejado, invitados, telefono, comentario, estado,
                                      motivo_rechazo, evento_id, resuelta_por, resuelta_en, creado_en, actualizado_en)
    VALUES (pg_temp.uid(s.email), v_ini, v_fin, s.festejado, s.invitados, s.telefono, s.comentario, s.estado, s.motivo, v_ev,
            CASE WHEN s.estado IN ('APROBADA', 'RECHAZADA') THEN CASE WHEN s.dia % 2 = 0 THEN v_admin ELSE v_vend END END,
            CASE WHEN s.estado IN ('APROBADA', 'RECHAZADA') THEN v_resuelta END,
            v_creada,
            CASE WHEN s.estado = 'PENDIENTE' THEN v_creada ELSE v_resuelta END);
  END LOOP;
END $$;

-- =====================================================================================================
-- 6. Funciones para crear pedidos coherentes (ítems, kardex, inscripciones, pagos y Webpay)
-- =====================================================================================================

-- Medio de pago de mostrador con la misma mezcla que mock-sales.ts del front.
CREATE OR REPLACE FUNCTION pg_temp.metodo_mostrador(p_seed text, p_antiguo boolean) RETURNS text LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE
    WHEN x < 0.30 THEN 'efectivo'
    WHEN x < 0.60 THEN 'debito'
    WHEN x < 0.77 THEN 'transferencia'
    WHEN x < 0.87 THEN CASE WHEN p_antiguo AND pg_temp.rnd(p_seed || 'tc') < 0.5 THEN 'tarjeta-credito' ELSE 'credito' END
    WHEN x < 0.93 THEN 'cartas-tcg'
    WHEN x < 0.97 THEN 'prepago'
    ELSE 'nota-credito' END
  FROM (SELECT pg_temp.rnd(p_seed || 'm') AS x) s
$$;

-- Inserta un pago con los campos obligatorios de su método.
CREATE OR REPLACE FUNCTION pg_temp.insertar_pago(p_ped bigint, p_metodo text, p_monto bigint, p_ts timestamptz,
                                                 p_ven bigint, p_seed text) RETURNS void LANGUAGE plpgsql AS $f$
DECLARE
  v_recibido bigint; v_archivo uuid; v_i int; v_valor bigint;
  v_cartas text[] := ARRAY['Charizard ex 199/165|SV3-199', 'Pikachu VMAX 188/185|SWSH4-188', 'Umbreon VMAX 215/203|SWSH7-215',
                           'Mewtwo ex 205/165|MEW-205', 'Gardevoir ex 245/198|SVI-245', 'Monkey D. Luffy OP05-119|OP05-119'];
BEGIN
  IF p_metodo = 'efectivo' THEN
    v_recibido := CASE WHEN pg_temp.rnd(p_seed || 'ef') < 0.35 THEN p_monto                       -- pago justo
                       WHEN p_monto < 10000 THEN ceil(p_monto / 5000.0)::bigint * 5000
                       ELSE ceil(p_monto / 10000.0)::bigint * 10000 END;
    INSERT INTO pago (pedido_id, metodo, monto, efectivo_recibido, vuelto_entregado, creado_en)
    VALUES (p_ped, 'efectivo', p_monto, v_recibido, v_recibido - p_monto, p_ts);

  ELSIF p_metodo IN ('debito', 'credito', 'prepago') THEN
    INSERT INTO pago (pedido_id, metodo, monto, numero_boleta, creado_en)
    VALUES (p_ped, p_metodo, p_monto, nextval('demo_boleta')::text, p_ts);

  ELSIF p_metodo = 'transferencia' THEN
    v_archivo := gen_random_uuid();
    INSERT INTO archivo (id, bucket, clave, mime, bytes, ancho, alto, sha256, subido_por, creado_en)
    VALUES (v_archivo, 'private-docs',
            'comprobantes/' || to_char(p_ts AT TIME ZONE 'America/Santiago', 'YYYY/MM/') || v_archivo || '.jpg',
            'image/jpeg', 120000 + floor(pg_temp.rnd(p_seed || 'tb') * 300000)::int, 1080, 1920,
            encode(sha256(convert_to(v_archivo::text, 'UTF8')), 'hex'), p_ven, p_ts - interval '2 minutes');
    INSERT INTO pago (pedido_id, metodo, monto, comprobante_archivo_id, creado_en)
    VALUES (p_ped, 'transferencia', p_monto, v_archivo, p_ts);

  ELSIF p_metodo = 'cartas-tcg' THEN
    v_i     := 1 + floor(pg_temp.rnd(p_seed || 'ct') * array_length(v_cartas, 1))::int;
    v_valor := CASE WHEN pg_temp.rnd(p_seed || 'cv') < 0.5 THEN p_monto
                    ELSE ceil(p_monto / 5000.0)::bigint * 5000 + 5000 END;   -- carta vale más: queda saldo a favor
    INSERT INTO pago (pedido_id, metodo, monto, carta_nombre, carta_codigo, carta_valor, saldo_a_favor, creado_en)
    VALUES (p_ped, 'cartas-tcg', p_monto, split_part(v_cartas[v_i], '|', 1), split_part(v_cartas[v_i], '|', 2),
            v_valor, v_valor - p_monto, p_ts);

  ELSE  -- nota-credito, tarjeta-credito (solo ventas antiguas) y webpay
    INSERT INTO pago (pedido_id, metodo, monto, creado_en) VALUES (p_ped, p_metodo, p_monto, p_ts);
  END IF;
END $f$;

-- Transacción de Webpay Plus con datos verosímiles del ambiente de integración (nunca números de tarjeta).
CREATE OR REPLACE FUNCTION pg_temp.tx_webpay(p_ped bigint, p_estado text, p_monto bigint, p_ts timestamptz, p_cli bigint)
RETURNS void LANGUAGE plpgsql AS $f$
DECLARE
  v_auth    boolean := p_estado IN ('AUTORIZADA', 'REVERSADA', 'ANULADA');
  v_detalle boolean := p_estado IN ('AUTORIZADA', 'REVERSADA', 'ANULADA', 'RECHAZADA');
  v_tipo    text    := (ARRAY['VD', 'VD', 'VN', 'VC', 'S2'])[1 + floor(pg_temp.rnd('pt' || p_ped) * 5)::int];
  v_cod     text    := lpad((abs(hashtext('auth' || p_ped)::bigint) % 1000000)::text, 6, '0');
  v_sesion  text    := 'S' || p_cli || '-' || floor(extract(epoch FROM p_ts))::bigint;
  v_tx_ts   timestamptz := p_ts + interval '90 seconds';
BEGIN
  INSERT INTO transaccion_webpay (pedido_id, buy_order, session_id, token, monto, estado, response_code,
                                  authorization_code, payment_type_code, installments_number, card_last4,
                                  accounting_date, transaction_date, respuesta_commit, respuesta_reembolso,
                                  creado_en, confirmado_en)
  VALUES (
    p_ped, 'TMP' || p_ped, v_sesion, encode(sha256(convert_to('token-' || p_ped, 'UTF8')), 'hex'), p_monto, p_estado,
    CASE WHEN v_auth THEN 0 WHEN p_estado = 'RECHAZADA' THEN -1 END,
    CASE WHEN v_auth THEN v_cod END,
    CASE WHEN v_detalle THEN v_tipo END,
    CASE WHEN v_detalle THEN CASE WHEN v_tipo = 'VC' THEN 3 ELSE 0 END END,
    CASE WHEN v_detalle THEN '6623' END,
    CASE WHEN v_detalle THEN to_char(p_ts AT TIME ZONE 'America/Santiago', 'MMDD') END,
    CASE WHEN v_detalle THEN v_tx_ts END,
    CASE WHEN v_detalle THEN jsonb_build_object(
      'vci', CASE WHEN v_auth THEN 'TSY' ELSE 'TSN' END, 'amount', p_monto,
      'status', CASE WHEN v_auth THEN 'AUTHORIZED' ELSE 'FAILED' END,
      'buy_order', 'TMP' || p_ped, 'session_id', v_sesion, 'card_detail', jsonb_build_object('card_number', '6623'),
      'accounting_date', to_char(p_ts AT TIME ZONE 'America/Santiago', 'MMDD'), 'transaction_date', v_tx_ts,
      'authorization_code', CASE WHEN v_auth THEN v_cod ELSE '000000' END, 'payment_type_code', v_tipo,
      'response_code', CASE WHEN v_auth THEN 0 ELSE -1 END,
      'installments_number', CASE WHEN v_tipo = 'VC' THEN 3 ELSE 0 END) END,
    CASE p_estado
      WHEN 'REVERSADA' THEN jsonb_build_object('type', 'REVERSED')
      WHEN 'ANULADA'   THEN jsonb_build_object('type', 'NULLIFIED', 'authorization_code', lpad((abs(hashtext('null' || p_ped)::bigint) % 1000000)::text, 6, '0'),
                                               'nullified_amount', p_monto, 'balance', 0, 'response_code', 0) END,
    p_ts,
    CASE p_estado WHEN 'CREADA' THEN NULL
                  WHEN 'EXPIRADA' THEN p_ts + interval '16 minutes'
                  WHEN 'ANULADA_USUARIO' THEN p_ts + interval '3 minutes'
                  WHEN 'ERROR' THEN p_ts + interval '4 minutes'
                  ELSE p_ts + interval '2 minutes' END);
END $f$;

-- Crea un pedido completo. Canal 'web' (Webpay) o 'tienda' (POS con pagos de mostrador).
--   p_prods / p_cants : productos y cantidades
--   p_evento / p_cant_ev : inscripción a un evento (opcional)
--   p_tx    : fuerza el estado de la transacción Webpay (si no, se deduce del estado del pedido)
--   p_pagos : fuerza los pagos de mostrador, p. ej. '[{"metodo":"efectivo","monto":10000},{"metodo":"debito"}]'
--             (el último sin monto cubre el resto)
CREATE OR REPLACE FUNCTION pg_temp.pedido(
  p_canal text, p_cli bigint, p_ven bigint, p_ts timestamptz, p_estado text,
  p_prods bigint[], p_cants int[],
  p_evento bigint DEFAULT NULL, p_cant_ev int DEFAULT 0,
  p_seed text DEFAULT '', p_tx text DEFAULT NULL, p_pagos jsonb DEFAULT NULL)
RETURNS bigint LANGUAGE plpgsql AS $f$
DECLARE
  v_admin   bigint := pg_temp.uid('admin@primerplayer.cl');
  v_pagado  boolean := p_estado IN ('PAGADO', 'LISTO_RETIRO', 'ENTREGADO', 'REEMBOLSADO')
                       OR (p_canal = 'tienda' AND p_estado = 'ANULADO');   -- venta POS anulada: se cobró y luego se anuló
  v_pago_ts timestamptz := CASE WHEN p_canal = 'web' THEN p_ts + interval '2 minutes' ELSE p_ts END;
  v_fin     timestamptz;  -- cuándo se repuso stock/cupos (estados finales sin venta)
  v_act     timestamptz;
  v_ped bigint; v_total bigint := 0; v_nombre text; v_precio bigint; v_i int;
  v_ev record; v_insc text; v_resto bigint; v_monto bigint; v_pago jsonb;
BEGIN
  v_fin := CASE p_estado
             WHEN 'EXPIRADO'    THEN p_ts + interval '16 minutes'
             WHEN 'RECHAZADO'   THEN p_ts + interval '4 minutes'
             WHEN 'ANULADO'     THEN p_ts + CASE WHEN p_canal = 'web' THEN interval '3 minutes' ELSE interval '50 minutes' END
             WHEN 'REEMBOLSADO' THEN p_ts + interval '2 days' END;
  v_fin := CASE WHEN v_fin IS NOT NULL THEN least(v_fin, now()) END;   -- ojo: least(NULL, x) = x
  v_act := CASE WHEN v_fin IS NOT NULL THEN v_fin
                WHEN p_estado = 'LISTO_RETIRO' THEN p_ts + interval '20 hours'
                WHEN p_estado = 'ENTREGADO' AND p_canal = 'web' THEN p_ts + interval '2 days'
                WHEN v_pagado THEN v_pago_ts
                ELSE p_ts END;
  v_act := greatest(p_ts, least(v_act, now()));

  INSERT INTO pedido (codigo, cliente_id, vendedor_id, canal, estado, total, idempotency_key, idempotency_hash,
                      expira_en, pagado_en, creado_en, actualizado_en)
  VALUES ('TMP-' || nextval('demo_pedido_tmp'), p_cli, CASE WHEN p_canal = 'tienda' THEN p_ven END, p_canal, p_estado, 0,
          gen_random_uuid(), encode(sha256(convert_to(p_seed || p_ts::text, 'UTF8')), 'hex'),
          CASE WHEN p_canal = 'web' AND p_estado IN ('PENDIENTE_PAGO', 'EXPIRADO') THEN p_ts + interval '15 minutes' END,
          CASE WHEN v_pagado THEN v_pago_ts END, p_ts, v_act)
  RETURNING id INTO v_ped;

  -- Productos + kardex
  FOR v_i IN 1 .. coalesce(array_length(p_prods, 1), 0) LOOP
    SELECT nombre, precio INTO v_nombre, v_precio FROM producto WHERE id = p_prods[v_i];
    INSERT INTO pedido_item (pedido_id, tipo, producto_id, nombre, cantidad, precio_unitario)
    VALUES (v_ped, 'product', p_prods[v_i], v_nombre, p_cants[v_i], v_precio);
    v_total := v_total + v_precio * p_cants[v_i];

    INSERT INTO demo_mov (producto_id, tipo, cantidad, pedido_id, usuario_id, creado_en)
    VALUES (p_prods[v_i], CASE p_canal WHEN 'web' THEN 'VENTA_WEB' ELSE 'VENTA_MOSTRADOR' END, -p_cants[v_i], v_ped,
            coalesce(p_ven, p_cli), p_ts);

    IF v_fin IS NOT NULL THEN
      INSERT INTO demo_mov (producto_id, tipo, cantidad, pedido_id, motivo, usuario_id, creado_en)
      VALUES (p_prods[v_i], CASE WHEN p_estado = 'EXPIRADO' THEN 'RESERVA_EXPIRADA' ELSE 'ANULACION' END, p_cants[v_i], v_ped,
              CASE p_estado WHEN 'EXPIRADO'  THEN 'Reserva vencida sin pago'
                            WHEN 'RECHAZADO' THEN 'Pago rechazado en Webpay'
                            WHEN 'ANULADO'   THEN CASE WHEN p_canal = 'web' THEN 'Pago anulado por el cliente'
                                                       ELSE 'Anulación de venta en mostrador' END
                            ELSE 'Reembolso del pedido' END,
              CASE WHEN p_estado = 'REEMBOLSADO' OR (p_estado = 'ANULADO' AND p_canal = 'tienda') THEN v_admin END,
              v_fin);
    END IF;
  END LOOP;

  -- Evento + inscripción (con snapshot)
  IF p_evento IS NOT NULL THEN
    SELECT * INTO v_ev FROM evento WHERE id = p_evento;
    INSERT INTO pedido_item (pedido_id, tipo, evento_id, nombre, cantidad, precio_unitario)
    VALUES (v_ped, 'event', p_evento, v_ev.titulo, p_cant_ev, v_ev.precio);
    v_total := v_total + v_ev.precio * p_cant_ev;
    v_insc := CASE WHEN p_estado = 'PENDIENTE_PAGO' THEN 'RESERVADA'
                   WHEN p_estado = 'EXPIRADO' THEN 'EXPIRADA'
                   WHEN p_estado IN ('PAGADO', 'LISTO_RETIRO', 'ENTREGADO') THEN 'CONFIRMADA'
                   ELSE 'CANCELADA' END;
    INSERT INTO inscripcion (evento_id, cliente_id, pedido_id, cantidad, estado, titulo_snapshot, fecha_snapshot,
                             lugar_snapshot, precio_snapshot, creado_en, cancelada_en)
    VALUES (p_evento, p_cli, v_ped, p_cant_ev, v_insc, v_ev.titulo, v_ev.fecha_hora, v_ev.lugar, v_ev.precio, p_ts,
            CASE WHEN v_insc = 'CANCELADA' THEN v_fin END);
  END IF;

  UPDATE pedido SET total = v_total WHERE id = v_ped;

  -- Pagos
  IF p_canal = 'web' THEN
    IF v_total > 0 THEN   -- total 0 (evento gratuito) se confirma sin Webpay
      PERFORM pg_temp.tx_webpay(v_ped,
        coalesce(p_tx, CASE p_estado WHEN 'PENDIENTE_PAGO' THEN 'CREADA' WHEN 'RECHAZADO' THEN 'RECHAZADA'
                                     WHEN 'ANULADO' THEN 'ANULADA_USUARIO' WHEN 'EXPIRADO' THEN 'EXPIRADA'
                                     WHEN 'REEMBOLSADO' THEN 'ANULADA' ELSE 'AUTORIZADA' END),
        v_total, p_ts, p_cli);
      IF v_pagado THEN
        INSERT INTO pago (pedido_id, metodo, monto, creado_en) VALUES (v_ped, 'webpay', v_total, v_pago_ts);
      END IF;
    END IF;
  ELSIF v_total > 0 THEN
    IF p_pagos IS NOT NULL THEN
      v_resto := v_total;
      FOR v_pago IN SELECT value FROM jsonb_array_elements(p_pagos) LOOP
        v_monto := coalesce((v_pago ->> 'monto')::bigint, v_resto);
        PERFORM pg_temp.insertar_pago(v_ped, v_pago ->> 'metodo', v_monto, p_ts, p_ven, p_seed || (v_pago ->> 'metodo'));
        v_resto := v_resto - v_monto;
      END LOOP;
    ELSIF v_total >= 15000 AND pg_temp.rnd(p_seed || 'mix') < 0.12 THEN   -- pago mixto efectivo + débito
      v_monto := (round(v_total * 0.4 / 1000) * 1000)::bigint;
      PERFORM pg_temp.insertar_pago(v_ped, 'efectivo', v_monto, p_ts, p_ven, p_seed || 'a');
      PERFORM pg_temp.insertar_pago(v_ped, 'debito', v_total - v_monto, p_ts, p_ven, p_seed || 'b');
    ELSE
      PERFORM pg_temp.insertar_pago(v_ped, pg_temp.metodo_mostrador(p_seed, p_ts < now() - interval '60 days'),
                                    v_total, p_ts, p_ven, p_seed);
    END IF;
  END IF;

  RETURN v_ped;
END $f$;

-- =====================================================================================================
-- 7. Casos de prueba puntuales (cada uno ejercita una regla o un estado concreto)
-- =====================================================================================================
DO $$
DECLARE
  v_hoy  date   := pg_temp.hoy();
  c      bigint := pg_temp.uid('cliente@primerplayer.cl');
  v_vend bigint := pg_temp.uid('vendedor@primerplayer.cl');
  v_vda  bigint := pg_temp.uid('vendedora@demo.primerplayer.cl');
  v_most bigint := pg_temp.uid('mostrador@primerplayer.cl');
BEGIN
  -- cliente@: llega al límite de la Elite Trainer Box (2) y compra 6 de 8 sobres Black Bolt permitidos.
  PERFORM pg_temp.pedido('web', c, NULL, pg_temp.ts(v_hoy - 20, 19.5), 'ENTREGADO',
    ARRAY[pg_temp.pid('Elite Trainer Box Phantasmal Flames'), pg_temp.pid('Sobre Pokémon Black Bolt (Español)')], ARRAY[2, 6],
    NULL, 0, 'caso-limite');
  -- cliente@: pedido listo para retiro.
  PERFORM pg_temp.pedido('web', c, NULL, pg_temp.ts(v_hoy - 1, 20.2), 'LISTO_RETIRO',
    ARRAY[pg_temp.pid('Figura Charizard Articulada'), pg_temp.pid('Pokémon Legends: Z-A')], ARRAY[1, 1], NULL, 0, 'caso-listo');
  -- cliente@: pago con error en el formulario de Webpay → RECHAZADO (transacción ERROR).
  PERFORM pg_temp.pedido('web', c, NULL, pg_temp.ts(v_hoy - 6, 22.1), 'RECHAZADO',
    ARRAY[pg_temp.pid('Control DualSense PS5 Blanco')], ARRAY[1], NULL, 0, 'caso-error', 'ERROR');
  -- cliente@: reembolso el mismo día → transacción REVERSADA.
  PERFORM pg_temp.pedido('web', c, NULL, pg_temp.ts(v_hoy - 30, 13.4), 'REEMBOLSADO',
    ARRAY[pg_temp.pid('Spider-Man 2')], ARRAY[1], NULL, 0, 'caso-reverso', 'REVERSADA');
  -- Tarjeta rechazada por el banco → RECHAZADO (transacción RECHAZADA).
  PERFORM pg_temp.pedido('web', pg_temp.uid('martina.morales@demo.primerplayer.cl'), NULL, pg_temp.ts(v_hoy - 12, 21.0), 'RECHAZADO',
    ARRAY[pg_temp.pid('Audífonos Pulse 3D PS5')], ARRAY[1], NULL, 0, 'caso-rechazo');
  -- El cliente abandona el pago en Webpay → ANULADO (transacción ANULADA_USUARIO).
  PERFORM pg_temp.pedido('web', pg_temp.uid('tomas.silva@demo.primerplayer.cl'), NULL, pg_temp.ts(v_hoy - 9, 18.3), 'ANULADO',
    ARRAY[pg_temp.pid('Astro Bot')], ARRAY[1], NULL, 0, 'caso-abandono');
  -- Camila cancela su inscripción a la League Challenge → reembolso (transacción ANULADA), inscripción CANCELADA.
  PERFORM pg_temp.pedido('web', pg_temp.uid('camila.rojas@demo.primerplayer.cl'), NULL, pg_temp.ts(v_hoy - 4, 18.0), 'REEMBOLSADO',
    '{}', '{}', pg_temp.eid('League Challenge Pokémon TCG'), 1, 'caso-cancela');
  -- Diego reservó 2 cupos del prelanzamiento y no pagó → EXPIRADO, inscripción EXPIRADA.
  PERFORM pg_temp.pedido('web', pg_temp.uid('diego.alarcon@demo.primerplayer.cl'), NULL, pg_temp.ts(v_hoy - 3, 23.2), 'EXPIRADO',
    '{}', '{}', pg_temp.eid('Prelanzamiento nueva expansión Pokémon'), 2, 'caso-expira');
  -- Valentina: pedido mixto (producto + evento).
  PERFORM pg_temp.pedido('web', pg_temp.uid('valentina.perez@demo.primerplayer.cl'), NULL, pg_temp.ts(v_hoy - 2, 17.3), 'PAGADO',
    ARRAY[pg_temp.pid('Sobre One Piece Card Game OP-10')], ARRAY[3], pg_temp.eid('Torneo Yu-Gi-Oh! Edison Format'), 1, 'caso-mixto');
  -- Historial de cuentas especiales: inactiva, eliminada y bloqueada.
  PERFORM pg_temp.pedido('web', pg_temp.uid('antonia.vargas@demo.primerplayer.cl'), NULL, pg_temp.ts(v_hoy - 70, 16.0), 'ENTREGADO',
    ARRAY[pg_temp.pid('Mazo de Inicio Mega Evolution')], ARRAY[1], NULL, 0, 'caso-inactiva');
  PERFORM pg_temp.pedido('web', pg_temp.uid('lucas.torres@demo.primerplayer.cl'), NULL, pg_temp.ts(v_hoy - 50, 12.5), 'ENTREGADO',
    ARRAY[pg_temp.pid('Halo Infinite')], ARRAY[1], NULL, 0, 'caso-eliminado');
  PERFORM pg_temp.pedido('web', pg_temp.uid('joaquin.herrera@demo.primerplayer.cl'), NULL, pg_temp.ts(v_hoy - 15, 20.0), 'ENTREGADO',
    ARRAY[pg_temp.pid('Carpeta Pokémon Pikachu 9 bolsillos')], ARRAY[1], NULL, 0, 'caso-bloqueado');
  -- POS: consola pagada con 3 medios (efectivo + carta TCG con saldo a favor + débito).
  PERFORM pg_temp.pedido('tienda', v_most, v_vend, pg_temp.ts(v_hoy - 4, 17.5), 'ENTREGADO',
    ARRAY[pg_temp.pid('PlayStation 5 Slim 1 TB')], ARRAY[1], NULL, 0, 'caso-pos-mixto', NULL,
    '[{"metodo": "efectivo", "monto": 100000}, {"metodo": "cartas-tcg", "monto": 89990}, {"metodo": "debito"}]');
  -- POS: 4 cupos de la League Cup vendidos en mostrador (2 anónimos, 2 a un cliente registrado).
  PERFORM pg_temp.pedido('tienda', v_most, v_vend, pg_temp.ts(v_hoy - 6, 16.0), 'PAGADO',
    '{}', '{}', pg_temp.eid('League Cup Pokémon TCG'), 2, 'caso-pos-ev1', NULL, '[{"metodo": "efectivo"}]');
  PERFORM pg_temp.pedido('tienda', pg_temp.uid('benjamin.gonzalez@demo.primerplayer.cl'), v_vda, pg_temp.ts(v_hoy - 3, 15.2), 'PAGADO',
    '{}', '{}', pg_temp.eid('League Cup Pokémon TCG'), 2, 'caso-pos-ev2', NULL, '[{"metodo": "debito"}]');
  -- cliente@: checkout en curso (hace 3 minutos): reserva stock y 1 cupo, vence en ~12 minutos.
  --   Con esto llega a 8/8 sobres Black Bolt: un tercer intento debe responder purchase-limit.
  PERFORM pg_temp.pedido('web', c, NULL, now() - interval '3 minutes', 'PENDIENTE_PAGO',
    ARRAY[pg_temp.pid('Sobre Pokémon Black Bolt (Español)')], ARRAY[2], pg_temp.eid('League Challenge Pokémon TCG'), 1, 'caso-pendiente');
END $$;

-- =====================================================================================================
-- 8. Historial de 90 días: ventas de mostrador y pedidos web (deterministas)
-- =====================================================================================================
DO $$
DECLARE
  v_hoy        date   := pg_temp.hoy();
  v_hora_ahora float8 := extract(epoch FROM (now() AT TIME ZONE 'America/Santiago')::time) / 3600;
  v_mostrador  bigint := pg_temp.uid('mostrador@primerplayer.cl');
  v_vendedores bigint[] := ARRAY[pg_temp.uid('vendedor@primerplayer.cl'), pg_temp.uid('vendedor@primerplayer.cl'),
                                 pg_temp.uid('vendedora@demo.primerplayer.cl'), pg_temp.uid('admin@primerplayer.cl')];
  v_clientes bigint[]; v_elig bigint[]; v_prods bigint[]; v_cants int[];
  d date; v_i int; k int; l int; n int; v_edad int; v_seed text; v_h float8; v_r float8; v_pid bigint;
  v_estado text; v_tx text; v_cli bigint;
  v_escala int := current_setting('demo.escala')::int;   -- pruebas de carga: N veces las ventas diarias
BEGIN
  SELECT array_agg(id ORDER BY id) INTO v_clientes FROM usuario
   WHERE rol = 'CLIENTE' AND NOT es_anonimo AND activo AND eliminado_en IS NULL
     AND email_verificado_en IS NOT NULL AND bloqueado_hasta IS NULL;

  FOR v_i IN 0 .. 89 LOOP
    d      := v_hoy - 89 + v_i;
    v_edad := v_hoy - d;
    -- Vendibles ese día: publicados, sin límite por cliente, ya ingresados y no eliminados todavía.
    SELECT array_agg(id ORDER BY id) INTO v_elig FROM producto
     WHERE publicado AND limite_por_cliente IS NULL AND fecha_ingreso <= d
       AND (eliminado_en IS NULL OR d < (eliminado_en AT TIME ZONE 'America/Santiago')::date);
    CONTINUE WHEN v_elig IS NULL;

    -- Mostrador: 3 a 9 ventas (domingo 1 a 3), de 11:00 a 20:00.
    n := CASE WHEN extract(dow FROM d) = 0 THEN 1 + floor(pg_temp.rnd('nt' || d) * 3)
              ELSE 3 + floor(pg_temp.rnd('nt' || d) * 7) END;
    n := n * v_escala;
    FOR k IN 1 .. n LOOP
      v_seed := 't' || d || '-' || k;
      v_h    := 11 + pg_temp.rnd(v_seed || 'h') * 9;
      CONTINUE WHEN d = v_hoy AND v_h > v_hora_ahora - 0.25;   -- hoy, solo ventas ya ocurridas
      v_prods := '{}'; v_cants := '{}';
      FOR l IN 1 .. 1 + floor(pg_temp.rnd(v_seed || 'nl') * 3)::int LOOP
        v_pid := v_elig[1 + floor(pg_temp.rnd(v_seed || 'p' || l) * array_length(v_elig, 1))::int];
        CONTINUE WHEN v_pid = ANY (v_prods);
        v_prods := v_prods || v_pid;
        v_cants := v_cants || (1 + floor(pg_temp.rnd(v_seed || 'q' || l) * 2))::int;
      END LOOP;
      PERFORM pg_temp.pedido('tienda',
        CASE WHEN pg_temp.rnd(v_seed || 'c') < 0.75 THEN v_mostrador
             ELSE v_clientes[1 + floor(pg_temp.rnd(v_seed || 'cc') * array_length(v_clientes, 1))::int] END,
        v_vendedores[1 + floor(pg_temp.rnd(v_seed || 'v') * 4)::int],
        pg_temp.ts(d, v_h),
        CASE WHEN v_edad > 0 AND pg_temp.rnd(v_seed || 'an') < 0.02 THEN 'ANULADO' ELSE 'ENTREGADO' END,
        v_prods, v_cants, NULL, 0, v_seed);
    END LOOP;

    -- Web: 0 a 3 pedidos, de 9:00 a 23:00.
    v_r := pg_temp.rnd('nw' || d);
    n := v_escala * CASE WHEN v_r < 0.30 THEN 0 WHEN v_r < 0.70 THEN 1 WHEN v_r < 0.92 THEN 2 ELSE 3 END;
    FOR k IN 1 .. n LOOP
      v_seed := 'w' || d || '-' || k;
      v_h    := 9 + pg_temp.rnd(v_seed || 'h') * 14;
      CONTINUE WHEN d = v_hoy AND v_h > v_hora_ahora - 0.5;
      v_prods := '{}'; v_cants := '{}';
      FOR l IN 1 .. 1 + floor(pg_temp.rnd(v_seed || 'nl') * 2)::int LOOP
        v_pid := v_elig[1 + floor(pg_temp.rnd(v_seed || 'p' || l) * array_length(v_elig, 1))::int];
        CONTINUE WHEN v_pid = ANY (v_prods);
        v_prods := v_prods || v_pid;
        v_cants := v_cants || (1 + floor(pg_temp.rnd(v_seed || 'q' || l) * 2))::int;
      END LOOP;
      v_cli := v_clientes[1 + floor(pg_temp.rnd(v_seed || 'c') * array_length(v_clientes, 1))::int];
      v_r   := pg_temp.rnd(v_seed || 'e');
      v_estado := CASE
        WHEN v_r < 0.05 THEN 'RECHAZADO'
        WHEN v_r < 0.09 THEN 'EXPIRADO'
        WHEN v_r < 0.12 THEN 'ANULADO'
        WHEN v_r < 0.14 AND v_edad > 3 THEN 'REEMBOLSADO'
        WHEN v_edad <= 1 THEN CASE WHEN pg_temp.rnd(v_seed || 'l') < 0.5 THEN 'PAGADO' ELSE 'LISTO_RETIRO' END
        WHEN v_edad <= 4 THEN CASE WHEN pg_temp.rnd(v_seed || 'l') < 0.4 THEN 'LISTO_RETIRO' ELSE 'ENTREGADO' END
        ELSE 'ENTREGADO' END;
      v_tx := CASE WHEN v_estado = 'RECHAZADO' AND pg_temp.rnd(v_seed || 'x') < 0.3 THEN 'ERROR' END;
      PERFORM pg_temp.pedido('web', v_cli, NULL, pg_temp.ts(d, v_h), v_estado, v_prods, v_cants, NULL, 0, v_seed, v_tx);
    END LOOP;
  END LOOP;
END $$;

-- =====================================================================================================
-- 9. Inscripciones a eventos hasta la ocupación objetivo (incluye las de los casos puntuales)
-- =====================================================================================================
DO $$
DECLARE
  v_clientes bigint[]; v_n int; e record; v_rest int; v_q int; j int; v_cli bigint; v_ts timestamptz;
BEGIN
  SELECT array_agg(id ORDER BY id) INTO v_clientes FROM usuario
   WHERE rol = 'CLIENTE' AND NOT es_anonimo AND activo AND eliminado_en IS NULL
     AND email_verificado_en IS NOT NULL AND bloqueado_hasta IS NULL;
  v_n := array_length(v_clientes, 1);

  FOR e IN
    SELECT ev.id, ev.fecha_hora, ev.limite_por_cliente, t.objetivo
      FROM evento ev
      JOIN (VALUES ('League Challenge Pokémon TCG', 9),               -- con 1 reserva pendiente y 1 cancelada
                   ('Prelanzamiento nueva expansión Pokémon', 11),    -- casi lleno (11/12)
                   ('League Cup Pokémon TCG', 20),                    -- LLENO: prueba event-full
                   ('Torneo Yu-Gi-Oh! Edison Format', 10),            -- límite 1 por cliente
                   ('Lanzamiento One Piece OP-11', 12),               -- gratuito (pedidos de total 0)
                   ('Noche de Mario Kart World', 8),
                   ('League Challenge Pokémon TCG de apertura', 14),  -- pasado: prueba event-past
                   ('Prelanzamiento Mega Evolution', 12)              -- pasado y lleno
           ) AS t(titulo, objetivo) ON t.titulo = ev.titulo
  LOOP
    SELECT e.objetivo - coalesce(sum(cantidad), 0) INTO v_rest
      FROM inscripcion WHERE evento_id = e.id AND estado IN ('RESERVADA', 'CONFIRMADA');
    j := 0;
    WHILE v_rest > 0 LOOP
      j := j + 1;
      IF j > v_n THEN RAISE EXCEPTION 'No alcanzan los clientes demo para llenar el evento %', e.id; END IF;
      v_cli := v_clientes[j];
      CONTINUE WHEN EXISTS (SELECT 1 FROM inscripcion WHERE evento_id = e.id AND cliente_id = v_cli);
      v_q := CASE WHEN e.limite_por_cliente = 1 THEN 1
                  WHEN v_rest > (v_n - j) THEN 2                       -- asegura llenar con los clientes que quedan
                  WHEN pg_temp.rnd('iq' || e.id || '-' || j) < 0.3 THEN 2
                  ELSE 1 END;
      v_q := least(v_q, v_rest, coalesce(e.limite_por_cliente, 99));
      v_ts := least(e.fecha_hora - interval '1 day', now() - interval '3 hours')
              - make_interval(hours => j * 7 + floor(pg_temp.rnd('ih' || e.id || '-' || j) * 5)::int);
      PERFORM pg_temp.pedido('web', v_cli, NULL, v_ts, 'PAGADO', '{}', '{}', e.id, v_q, 'ins' || e.id || '-' || j);
      v_rest := v_rest - v_q;
    END LOOP;
  END LOOP;
END $$;

UPDATE evento e
   SET cupos_ocupados = coalesce((SELECT sum(i.cantidad) FROM inscripcion i
                                   WHERE i.evento_id = e.id AND i.estado IN ('RESERVADA', 'CONFIRMADA')), 0);

-- =====================================================================================================
-- 10. Códigos de pedido definitivos (PP-AAAA-NNNNNN en orden cronológico) y referencias de Webpay
-- =====================================================================================================
WITH r AS (SELECT id, creado_en, row_number() OVER (ORDER BY creado_en, id) AS n FROM pedido)
UPDATE pedido p
   SET codigo = 'PP-' || to_char(r.creado_en AT TIME ZONE 'America/Santiago', 'YYYY') || '-' || lpad(r.n::text, 6, '0')
  FROM r WHERE r.id = p.id;

DO $$ BEGIN PERFORM setval('pedido_codigo_seq', (SELECT count(*) FROM pedido)); END $$;   -- la app sigue desde aquí

UPDATE transaccion_webpay t
   SET buy_order = p.codigo,
       respuesta_commit = CASE WHEN t.respuesta_commit IS NULL THEN NULL
                               ELSE jsonb_set(t.respuesta_commit, '{buy_order}', to_jsonb(p.codigo)) END
  FROM pedido p WHERE p.id = t.pedido_id;

-- =====================================================================================================
-- 11. Kardex: ajustes, reposiciones e ingreso inicial; el stock final queda igual al del front
-- =====================================================================================================
INSERT INTO demo_mov (producto_id, tipo, cantidad, motivo, usuario_id, creado_en)
SELECT pg_temp.pid(v.producto), v.tipo, v.cantidad, v.motivo, pg_temp.uid(v.email), pg_temp.ts(pg_temp.hoy() - v.dia, v.hora)
FROM (VALUES
  ('Peluche Pikachu 30 cm',                    'AJUSTE',  -1, 'Unidad dañada en vitrina',          'vendedor@primerplayer.cl',       12, 18.0),
  ('Protectores Standard Pikachu (65 un.)',    'AJUSTE',   3, 'Conteo de inventario: sobrante',    'vendedora@demo.primerplayer.cl', 8,  19.0),
  ('Mazo Battle Deck Charizard ex',            'AJUSTE',  -1, 'Unidad abierta para demostración',  'vendedor@primerplayer.cl',       20, 12.0),
  ('Sobre Pokémon Mega Evolution (Español)',   'INGRESO', 36, 'Reposición de proveedor',           'admin@primerplayer.cl',          30, 10.0),
  ('Sobre One Piece Card Game OP-10',          'INGRESO', 24, 'Reposición de proveedor',           'admin@primerplayer.cl',          25, 10.0),
  ('Lata Coleccionable Pikachu',               'INGRESO',  6, 'Reposición de proveedor',           'admin@primerplayer.cl',          14, 10.5)
) AS v(producto, tipo, cantidad, motivo, email, dia, hora);

DO $$
DECLARE
  p record; v_net int; v_min int; v_ini int; v_extra int; v_primero timestamptz;
  v_admin bigint := pg_temp.uid('admin@primerplayer.cl');
  v_vend  bigint := pg_temp.uid('vendedor@primerplayer.cl');
BEGIN
  FOR p IN SELECT id, stock, fecha_ingreso FROM producto LOOP
    SELECT coalesce(sum(cantidad), 0), min(creado_en) INTO v_net, v_primero FROM demo_mov WHERE producto_id = p.id;
    SELECT least(coalesce(min(s), 0), 0) INTO v_min
      FROM (SELECT sum(cantidad) OVER (ORDER BY creado_en, seq) AS s FROM demo_mov WHERE producto_id = p.id) x;
    -- Ingreso inicial = stock final − movimientos netos (+ margen si algún tramo quedara negativo).
    v_ini   := p.stock - v_net;
    v_extra := greatest(0, -(v_ini + v_min));
    v_ini   := v_ini + v_extra;
    IF v_ini = 0 THEN v_ini := 2; v_extra := v_extra + 2; END IF;   -- agotado sin ventas: llegó y se dio de baja
    INSERT INTO demo_mov (producto_id, tipo, cantidad, motivo, usuario_id, creado_en)
    VALUES (p.id, 'INGRESO', v_ini, 'Ingreso inicial de mercadería', v_admin,
            -- nunca en el futuro (de madrugada, "hoy a las 10:00" aún no ocurre) ni después de su primer movimiento
            least(pg_temp.ts(p.fecha_ingreso, 10), coalesce(v_primero, 'infinity'::timestamptz) - interval '1 hour',
                  now() - interval '3 hours'));
    IF v_extra > 0 THEN
      INSERT INTO demo_mov (producto_id, tipo, cantidad, motivo, usuario_id, creado_en)
      VALUES (p.id, 'AJUSTE', -v_extra, 'Ajuste por conteo de inventario', v_vend, now() - interval '2 hours');
    END IF;
  END LOOP;
END $$;

INSERT INTO movimiento_stock (producto_id, tipo, cantidad, stock_resultante, referencia, motivo, usuario_id, creado_en)
SELECT m.producto_id, m.tipo, m.cantidad,
       sum(m.cantidad) OVER (PARTITION BY m.producto_id ORDER BY m.creado_en, m.seq),
       p.codigo, m.motivo, m.usuario_id, m.creado_en
FROM demo_mov m
LEFT JOIN pedido p ON p.id = m.pedido_id
ORDER BY m.creado_en, m.seq;

-- =====================================================================================================
-- 12. Favoritos y alertas de stock
-- =====================================================================================================
INSERT INTO favorito (usuario_id, producto_id, creado_en)
SELECT pg_temp.uid('cliente@primerplayer.cl'), pg_temp.pid(v.producto), now() - make_interval(days => v.dias)
FROM (VALUES ('Umbreon ex', 3), ('Elite Trainer Box Ascended Heroes', 8), ('Monkey D. Luffy Gear 5 Figura', 15),
             ('Mario Kart World', 20), ('Control DualSense PS5 Blanco', 25), ('Playmat Charizard', 40)) AS v(producto, dias);

INSERT INTO favorito (usuario_id, producto_id, creado_en)
SELECT u.id, p.id, now() - make_interval(days => (abs(hashtext(u.id || '-' || p.id)::bigint) % 60)::int)
FROM usuario u CROSS JOIN producto p
WHERE u.email LIKE '%@demo.primerplayer.cl' AND u.rol = 'CLIENTE' AND p.publicado AND p.eliminado_en IS NULL
  AND pg_temp.rnd('fav' || u.id || '-' || p.id) < 0.03
ON CONFLICT DO NOTHING;

-- Pendientes: solo sobre productos agotados (se notifican cuando vuelva el stock).
INSERT INTO alerta_stock (usuario_id, producto_id, creado_en, notificado_en)
SELECT u.id, p.id, now() - make_interval(days => 1 + (abs(hashtext('al' || u.id || p.id)::bigint) % 20)::int), NULL
FROM usuario u CROSS JOIN producto p
WHERE p.stock = 0 AND p.publicado AND p.eliminado_en IS NULL
  AND u.rol = 'CLIENTE' AND NOT u.es_anonimo AND u.activo AND u.eliminado_en IS NULL
  AND pg_temp.rnd('al' || u.id || '-' || p.id) < CASE WHEN u.email = 'cliente@primerplayer.cl' THEN 0.5 ELSE 0.15 END;

-- Ya notificadas: productos que se agotaron y volvieron a tener stock.
INSERT INTO alerta_stock (usuario_id, producto_id, creado_en, notificado_en)
SELECT u.id, p.id, now() - interval '25 days', now() - make_interval(days => 2 + (abs(hashtext('aln' || u.id || p.id)::bigint) % 15)::int)
FROM usuario u CROSS JOIN producto p
WHERE p.stock > 0 AND p.publicado AND p.eliminado_en IS NULL
  AND u.rol = 'CLIENTE' AND NOT u.es_anonimo AND u.activo AND u.eliminado_en IS NULL
  AND pg_temp.rnd('aln' || u.id || '-' || p.id) < 0.02
ON CONFLICT DO NOTHING;

-- =====================================================================================================
-- 13. Cierres de caja: un cierre por día pasado con ventas en tienda, calculado desde los pagos reales.
--     HOY queda sin cerrar para probar el cierre desde la app.
-- =====================================================================================================
WITH ped AS (
  SELECT p.id, p.total, p.canal, (p.creado_en AT TIME ZONE 'America/Santiago')::date AS fecha
    FROM pedido p WHERE p.estado IN ('PAGADO', 'LISTO_RETIRO', 'ENTREGADO')
), dias AS (
  SELECT fecha, sum(total) AS total, count(*) AS n FROM ped
   GROUP BY fecha HAVING bool_or(canal = 'tienda') AND fecha < pg_temp.hoy()
), sub AS (
  SELECT fecha, jsonb_object_agg(metodo, monto ORDER BY metodo) AS subtotales
    FROM (SELECT ped.fecha, pg.metodo, sum(pg.monto) AS monto
            FROM ped JOIN pago pg ON pg.pedido_id = ped.id GROUP BY ped.fecha, pg.metodo) x
   GROUP BY fecha
), efe AS (   -- efectivo neto = Σ(recibido − vuelto); las ventas web no pasan por la caja física
  SELECT ped.fecha, sum(pg.efectivo_recibido - pg.vuelto_entregado) AS neto
    FROM ped JOIN pago pg ON pg.pedido_id = ped.id
   WHERE pg.metodo = 'efectivo' AND ped.canal = 'tienda' GROUP BY ped.fecha
), calc AS (
  SELECT d.fecha, d.total, d.n, coalesce(s.subtotales, '{}'::jsonb) AS subtotales, coalesce(e.neto, 0) AS neto,
         CASE WHEN pg_temp.rnd('caja' || d.fecha) < 0.85 THEN 0
              ELSE (ARRAY[-2000, -500, 1000, 3000])[1 + floor(pg_temp.rnd('dif' || d.fecha) * 4)::int] END AS dif
    FROM dias d LEFT JOIN sub s USING (fecha) LEFT JOIN efe e USING (fecha)
)
INSERT INTO cierre_caja (fecha, fondo_inicial, efectivo_contado, efectivo_neto, esperado_en_caja, total_ventas,
                         numero_ventas, diferencia, subtotales, observaciones, version, cerrado_por, cerrado_en)
SELECT fecha, 50000, 50000 + neto + dif, neto, 50000 + neto, total, n, dif, subtotales,
       CASE WHEN dif < 0 THEN 'Faltante en caja: se revisarán los vueltos del día.'
            WHEN dif > 0 THEN 'Sobrante en caja: posible vuelto no entregado.' ELSE '' END,
       1,
       CASE WHEN extract(dow FROM fecha)::int % 2 = 0 THEN pg_temp.uid('vendedor@primerplayer.cl')
            ELSE pg_temp.uid('vendedora@demo.primerplayer.cl') END,
       pg_temp.ts(fecha, 20.75)
FROM calc;

-- Un cierre corregido al día siguiente: la versión 1 queda en el historial.
INSERT INTO cierre_caja_version (fecha, version, datos, cerrado_por, cerrado_en)
SELECT fecha, 1,
       jsonb_build_object('fondoInicial', fondo_inicial, 'efectivoContado', efectivo_contado - 5000,
                          'efectivoNeto', efectivo_neto, 'esperadoEnCaja', esperado_en_caja,
                          'totalVentas', total_ventas, 'numeroVentas', numero_ventas,
                          'diferencia', diferencia - 5000, 'subtotales', subtotales,
                          'observaciones', 'Faltan $5.000 en el primer conteo.'),
       cerrado_por, cerrado_en
FROM cierre_caja WHERE fecha = (SELECT max(fecha) FROM cierre_caja WHERE fecha <= pg_temp.hoy() - 3);

UPDATE cierre_caja
   SET version = 2, cerrado_en = cerrado_en + interval '13 hours',
       observaciones = 'Recuento: el faltante inicial de $5.000 era un billete mal contado.'
 WHERE fecha = (SELECT max(fecha) FROM cierre_caja_version);

-- =====================================================================================================
-- 14. Auditoría
-- =====================================================================================================
INSERT INTO auditoria (ocurrido_en, usuario_id, accion, entidad, entidad_id, antes, despues, ip, request_id)
SELECT a.ocurrido_en, a.usuario_id, a.accion, a.entidad, a.entidad_id, a.antes, a.despues,
       ('200.73.112.' || (1 + abs(hashtext(a.entidad_id || a.accion)::bigint) % 250))::inet, gen_random_uuid()::text
FROM (
  -- Cambio de rol: Javiera pasa de cliente a vendedora
  SELECT u.creado_en + interval '1 day' AS ocurrido_en, pg_temp.uid('admin@primerplayer.cl') AS usuario_id,
         'ROL_CAMBIADO' AS accion, 'usuario' AS entidad, u.id::text AS entidad_id,
         '{"rol": "CLIENTE"}'::jsonb AS antes, '{"rol": "VENDEDOR"}'::jsonb AS despues
    FROM usuario u WHERE u.email = 'vendedora@demo.primerplayer.cl'
  UNION ALL  -- Cambio de precio
  SELECT pg_temp.ts(pg_temp.hoy() - 15, 10.2), pg_temp.uid('admin@primerplayer.cl'), 'PRECIO_CAMBIADO', 'producto',
         pg_temp.pid('EA Sports FC 26')::text, '{"precio": 59990}', '{"precio": 54990}'
  UNION ALL  -- Límites por cliente
  SELECT pg_temp.ts(pg_temp.hoy() - 40, 11), pg_temp.uid('admin@primerplayer.cl'), 'LIMITE_CAMBIADO', 'producto',
         p.id::text, '{"limitePorCliente": null}', jsonb_build_object('limitePorCliente', p.limite_por_cliente)
    FROM producto p WHERE p.limite_por_cliente IS NOT NULL
  UNION ALL  -- Despublicación
  SELECT pg_temp.ts(pg_temp.hoy() - 2, 12), pg_temp.uid('vendedor@primerplayer.cl'), 'PUBLICACION_CAMBIADA', 'producto',
         pg_temp.pid('Booster Box Phantasmal Flames (36 sobres)')::text, '{"publicado": true}', '{"publicado": false}'
  UNION ALL  -- Eliminaciones
  SELECT p.eliminado_en, pg_temp.uid('admin@primerplayer.cl'), 'PRODUCTO_ELIMINADO', 'producto', p.id::text,
         jsonb_build_object('nombre', p.nombre), NULL FROM producto p WHERE p.eliminado_en IS NOT NULL
  UNION ALL
  SELECT e.eliminado_en, pg_temp.uid('admin@primerplayer.cl'), 'EVENTO_ELIMINADO', 'evento', e.id::text,
         jsonb_build_object('titulo', e.titulo), NULL FROM evento e WHERE e.eliminado_en IS NOT NULL
  UNION ALL  -- Ajustes de stock
  SELECT m.creado_en, m.usuario_id, 'STOCK_AJUSTADO', 'producto', m.producto_id::text,
         jsonb_build_object('stock', m.stock_resultante - m.cantidad),
         jsonb_build_object('stock', m.stock_resultante, 'motivo', m.motivo)
    FROM movimiento_stock m WHERE m.tipo = 'AJUSTE'
  UNION ALL  -- Ventas POS
  SELECT p.creado_en, p.vendedor_id, 'VENTA_POS', 'pedido', p.codigo, NULL,
         jsonb_build_object('total', p.total, 'metodos', (SELECT jsonb_agg(DISTINCT pg.metodo) FROM pago pg WHERE pg.pedido_id = p.id))
    FROM pedido p WHERE p.canal = 'tienda'
  UNION ALL  -- Anulaciones POS y reembolsos web
  SELECT p.actualizado_en, pg_temp.uid('admin@primerplayer.cl'),
         CASE WHEN p.canal = 'tienda' THEN 'VENTA_ANULADA' ELSE 'PEDIDO_REEMBOLSADO' END, 'pedido', p.codigo,
         '{"estado": "PAGADO"}', jsonb_build_object('estado', p.estado)
    FROM pedido p WHERE (p.canal = 'tienda' AND p.estado = 'ANULADO') OR p.estado = 'REEMBOLSADO'
  UNION ALL  -- Cierres de caja
  SELECT c.cerrado_en, c.cerrado_por, 'CIERRE_CAJA', 'cierre_caja', c.fecha::text, NULL,
         jsonb_build_object('totalVentas', c.total_ventas, 'diferencia', c.diferencia, 'version', c.version)
    FROM cierre_caja c
  UNION ALL  -- Solicitudes de cumpleaños resueltas
  SELECT s.resuelta_en, s.resuelta_por,
         CASE s.estado WHEN 'APROBADA' THEN 'SOLICITUD_APROBADA' ELSE 'SOLICITUD_RECHAZADA' END,
         'solicitud_cumpleanos', s.id::text, '{"estado": "PENDIENTE"}', jsonb_build_object('estado', s.estado)
    FROM solicitud_cumpleanos s WHERE s.estado IN ('APROBADA', 'RECHAZADA')
  UNION ALL  -- Baneo manual (V9)
  SELECT u.baneo_creado_en, pg_temp.uid('admin@primerplayer.cl'),
         'USUARIO_BANEADO', 'usuario', u.id::text,
         '{"baneado": false}'::jsonb,
         jsonb_build_object('baneado', true, 'motivo', u.baneo_motivo, 'hasta',
                            to_char(u.baneo_hasta AT TIME ZONE 'America/Santiago', 'YYYY-MM-DD'))
    FROM usuario u WHERE u.baneo_motivo IS NOT NULL
  UNION ALL  -- Lista negra (V9)
  SELECT u.lista_negra_en, pg_temp.uid('admin@primerplayer.cl'),
         'LISTA_NEGRA_AGREGADO', 'usuario', u.id::text,
         '{"listaNegra": false}'::jsonb,
         jsonb_build_object('listaNegra', true, 'motivo', u.lista_negra_motivo)
    FROM usuario u WHERE u.lista_negra_motivo IS NOT NULL
  UNION ALL  -- Cambio de contraseña forzado (V9)
  SELECT now() - interval '1 day', pg_temp.uid('admin@primerplayer.cl'),
         'RESET_PASSWORD_SOLICITADO', 'usuario', u.id::text,
         '{"cambioPasswordPendiente": false}'::jsonb,
         '{"cambioPasswordPendiente": true}'::jsonb
    FROM usuario u WHERE u.cambio_password_pendiente AND u.email = 'fernanda.navarro@demo.primerplayer.cl'
) a;

-- =====================================================================================================
-- 15. Correos enviados (registro de notificaciones; Mailpit no los muestra porque no se enviaron de verdad)
-- =====================================================================================================
INSERT INTO correo_enviado (destino, plantilla, referencia, enviado_en, proveedor_id)
SELECT c.destino, c.plantilla, c.referencia, c.enviado_en, 'demo-' || md5(c.plantilla || c.referencia || c.destino)
FROM (
  SELECT u.email AS destino, 'verificacion-cuenta' AS plantilla, 'usuario-' || u.id AS referencia,
         coalesce(u.email_verificado_en, u.creado_en) - interval '5 minutes' AS enviado_en
    FROM usuario u WHERE NOT u.es_anonimo AND u.rol = 'CLIENTE'
  UNION ALL
  SELECT u.email, 'pedido-confirmado', p.codigo, p.pagado_en
    FROM pedido p JOIN usuario u ON u.id = p.cliente_id
   WHERE p.canal = 'web' AND p.pagado_en IS NOT NULL
  UNION ALL
  SELECT u.email, 'pedido-listo-retiro', p.codigo, least(p.creado_en + interval '20 hours', now())
    FROM pedido p JOIN usuario u ON u.id = p.cliente_id
   WHERE p.canal = 'web' AND p.estado IN ('LISTO_RETIRO', 'ENTREGADO') AND EXISTS (SELECT 1 FROM pedido_item i WHERE i.pedido_id = p.id AND i.tipo = 'product')
  UNION ALL
  SELECT u.email, 'alerta-stock', 'producto-' || a.producto_id, a.notificado_en
    FROM alerta_stock a JOIN usuario u ON u.id = a.usuario_id WHERE a.notificado_en IS NOT NULL
  UNION ALL
  SELECT u.email, 'solicitud-cumpleanos-' || lower(s.estado), 'solicitud-' || s.id, s.resuelta_en
    FROM solicitud_cumpleanos s JOIN usuario u ON u.id = s.cliente_id WHERE s.estado IN ('APROBADA', 'RECHAZADA')
  UNION ALL
  SELECT u.email, 'recuperacion-password', 'token-' || t.id, t.expira_en - interval '1 hour'
    FROM token_un_uso t JOIN usuario u ON u.id = t.usuario_id WHERE t.tipo = 'RECUPERACION'
) c;

-- =====================================================================================================
-- 15b. Último acceso desde pedidos (ahora que el historial de 90 días ya existe)
-- =====================================================================================================
UPDATE usuario u SET ultimo_acceso_en = GREATEST(u.ultimo_acceso_en, sub.ultimo)
FROM (
  SELECT cliente_id AS uid, max(creado_en) AS ultimo FROM pedido WHERE canal = 'web' GROUP BY cliente_id
  UNION ALL
  SELECT vendedor_id, max(creado_en) FROM pedido WHERE canal = 'tienda' AND vendedor_id IS NOT NULL GROUP BY vendedor_id
) sub WHERE u.id = sub.uid;

-- =====================================================================================================
-- 16. Validaciones finales: si algo no cuadra, se revierte TODO
-- =====================================================================================================
DO $$
DECLARE v int; v_falta text; r record; v_resumen text := '';
BEGIN
  -- Stock = stock_resultante del último movimiento
  SELECT count(*) INTO v FROM producto p
   WHERE p.stock IS DISTINCT FROM (SELECT m.stock_resultante FROM movimiento_stock m
                                    WHERE m.producto_id = p.id ORDER BY m.creado_en DESC, m.id DESC LIMIT 1);
  IF v > 0 THEN RAISE EXCEPTION 'Stock incoherente con el kardex en % productos', v; END IF;

  -- Cupos = inscripciones vigentes
  SELECT count(*) INTO v FROM evento e
   WHERE e.cupos_ocupados <> coalesce((SELECT sum(cantidad) FROM inscripcion i
                                        WHERE i.evento_id = e.id AND i.estado IN ('RESERVADA', 'CONFIRMADA')), 0);
  IF v > 0 THEN RAISE EXCEPTION 'Cupos incoherentes en % eventos', v; END IF;

  -- Total del pedido = suma de ítems; pagos = total
  SELECT count(*) INTO v FROM pedido p
   WHERE p.total <> coalesce((SELECT sum(subtotal) FROM pedido_item i WHERE i.pedido_id = p.id), 0);
  IF v > 0 THEN RAISE EXCEPTION 'Totales incoherentes en % pedidos', v; END IF;
  SELECT count(*) INTO v FROM pedido p
   WHERE EXISTS (SELECT 1 FROM pago g WHERE g.pedido_id = p.id)
     AND p.total <> (SELECT sum(monto) FROM pago g WHERE g.pedido_id = p.id);
  IF v > 0 THEN RAISE EXCEPTION 'Pagos que no cuadran en % pedidos', v; END IF;

  -- Límite por cliente respetado (pedidos válidos, sin contar el mostrador anónimo)
  SELECT count(*) INTO v FROM (
    SELECT p.cliente_id, i.producto_id, sum(i.cantidad) AS q, max(pr.limite_por_cliente) AS lim
      FROM pedido p JOIN pedido_item i ON i.pedido_id = p.id JOIN producto pr ON pr.id = i.producto_id
      JOIN usuario u ON u.id = p.cliente_id
     WHERE p.estado IN ('PENDIENTE_PAGO', 'PAGADO', 'LISTO_RETIRO', 'ENTREGADO') AND NOT u.es_anonimo
       AND pr.limite_por_cliente IS NOT NULL
     GROUP BY p.cliente_id, i.producto_id) x WHERE q > lim;
  IF v > 0 THEN RAISE EXCEPTION 'Límite por cliente superado en % casos', v; END IF;

  -- Ningún evento vigente se traslapa con un cumpleaños (eventos sin término ocupan 3 h)
  SELECT count(*) INTO v FROM evento c JOIN evento e ON e.id <> c.id
   WHERE c.tipo = 'cumpleanos' AND c.eliminado_en IS NULL AND e.eliminado_en IS NULL
     AND tstzrange(c.fecha_hora, c.fecha_hora_fin) && tstzrange(e.fecha_hora, coalesce(e.fecha_hora_fin, e.fecha_hora + interval '3 hours'));
  IF v > 0 THEN RAISE EXCEPTION 'Hay % traslapes con cumpleaños', v; END IF;

  -- Cobertura: cada estado y tipo aparece al menos una vez
  SELECT string_agg(x, ', ') INTO v_falta FROM unnest(ARRAY['PENDIENTE_PAGO','PAGADO','LISTO_RETIRO','ENTREGADO',
    'RECHAZADO','EXPIRADO','ANULADO','REEMBOLSADO']) x WHERE NOT EXISTS (SELECT 1 FROM pedido WHERE estado = x);
  IF v_falta IS NOT NULL THEN RAISE EXCEPTION 'Faltan estados de pedido: %', v_falta; END IF;
  SELECT string_agg(x, ', ') INTO v_falta FROM unnest(ARRAY['efectivo','debito','credito','prepago','transferencia',
    'cartas-tcg','nota-credito','tarjeta-credito','webpay']) x WHERE NOT EXISTS (SELECT 1 FROM pago WHERE metodo = x);
  IF v_falta IS NOT NULL THEN RAISE EXCEPTION 'Faltan métodos de pago: %', v_falta; END IF;
  SELECT string_agg(x, ', ') INTO v_falta FROM unnest(ARRAY['CREADA','AUTORIZADA','RECHAZADA','ANULADA_USUARIO',
    'EXPIRADA','ERROR','REVERSADA','ANULADA']) x WHERE NOT EXISTS (SELECT 1 FROM transaccion_webpay WHERE estado = x);
  IF v_falta IS NOT NULL THEN RAISE EXCEPTION 'Faltan estados de Webpay: %', v_falta; END IF;
  SELECT string_agg(x, ', ') INTO v_falta FROM unnest(ARRAY['RESERVADA','CONFIRMADA','CANCELADA','EXPIRADA']) x
   WHERE NOT EXISTS (SELECT 1 FROM inscripcion WHERE estado = x);
  IF v_falta IS NOT NULL THEN RAISE EXCEPTION 'Faltan estados de inscripción: %', v_falta; END IF;
  SELECT string_agg(x, ', ') INTO v_falta FROM unnest(ARRAY['PENDIENTE','APROBADA','RECHAZADA','CANCELADA']) x
   WHERE NOT EXISTS (SELECT 1 FROM solicitud_cumpleanos WHERE estado = x);
  IF v_falta IS NOT NULL THEN RAISE EXCEPTION 'Faltan estados de solicitud: %', v_falta; END IF;
  SELECT string_agg(x, ', ') INTO v_falta FROM unnest(ARRAY['VENTA_WEB','VENTA_MOSTRADOR','AJUSTE','ANULACION',
    'RESERVA_EXPIRADA','INGRESO']) x WHERE NOT EXISTS (SELECT 1 FROM movimiento_stock WHERE tipo = x);
  IF v_falta IS NOT NULL THEN RAISE EXCEPTION 'Faltan tipos de movimiento: %', v_falta; END IF;
  SELECT string_agg(x, ', ') INTO v_falta FROM unnest(ARRAY['league-cup','league-challenge','torneo-tcg','prelanzamiento',
    'lanzamiento','videojuegos','cumpleanos','otros']) x WHERE NOT EXISTS (SELECT 1 FROM evento WHERE tipo = x);
  IF v_falta IS NOT NULL THEN RAISE EXCEPTION 'Faltan tipos de evento: %', v_falta; END IF;

  -- V9: cobertura de estados de administración de usuarios
  IF NOT EXISTS (SELECT 1 FROM usuario WHERE baneo_motivo IS NOT NULL) THEN
    RAISE EXCEPTION 'Falta al menos un usuario baneado (V9)';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM usuario WHERE lista_negra_motivo IS NOT NULL) THEN
    RAISE EXCEPTION 'Falta al menos un usuario en lista negra (V9)';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM usuario WHERE cambio_password_pendiente) THEN
    RAISE EXCEPTION 'Falta al menos un usuario con cambio de contraseña pendiente (V9)';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM usuario WHERE ultimo_acceso_en IS NOT NULL) THEN
    RAISE EXCEPTION 'Falta al menos un usuario con ultimo_acceso_en registrado (V9)';
  END IF;

  -- Resumen: filas por tabla
  FOR r IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history' ORDER BY 1 LOOP
    EXECUTE format('SELECT count(*) FROM %I', r.tablename) INTO v;
    v_resumen := v_resumen || format(E'\n  %-22s %6s', r.tablename, v);
  END LOOP;
  RAISE NOTICE E'Datos demo cargados y validados. Filas por tabla:%', v_resumen;
END $$;

DROP SEQUENCE demo_pedido_tmp;
DROP SEQUENCE demo_boleta;

-- Contraseña propia para todas las cuentas (-v clave=...): un solo hash bcrypt (costo 12, el mismo de la app) con
-- pgcrypto, que se instala solo para esto y se quita al terminar si no estaba.
\if :{?clave}
SELECT NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pgcrypto') AS instalar_pgcrypto \gset
\if :instalar_pgcrypto
CREATE EXTENSION pgcrypto;
\endif
WITH h AS (SELECT crypt(:'clave', gen_salt('bf', 12)) AS hash)
UPDATE usuario SET password_hash = h.hash FROM h WHERE password_hash IS NOT NULL;
\if :instalar_pgcrypto
DROP EXTENSION pgcrypto;
\endif
\else
DO $$ BEGIN
  IF current_database() = 'runagard_staging' THEN
    RAISE EXCEPTION 'En staging hay que pasar -v clave=... : no se dejan cuentas con la contraseña publicada en el repo.';
  END IF;
END $$;
\endif

COMMIT;
