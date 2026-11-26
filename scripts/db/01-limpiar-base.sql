-- =====================================================================================================
-- Primer Player · 01 · Vaciar la base de datos (SOLO DESARROLLO LOCAL Y STAGING)
--
-- Deja TODAS las tablas vacías y reinicia los contadores (IDs y códigos de pedido).
-- Única fila que se conserva: el usuario administrador `admin@primerplayer.cl` (con su contraseña actual).
--
-- No se toca `flyway_schema_history`: no tiene datos de negocio, solo registra qué migraciones ya se aplicaron.
-- Si se vaciara, la app no volvería a arrancar (Flyway intentaría recrear tablas que ya existen).
--
-- También se borran las categorías, franquicias y el cliente de mostrador que siembra V6;
-- `02-poblar-base.sql` los restaura. Sin el 02, el catálogo queda sin categorías y el POS sin cliente de mostrador.
--
-- Uso (Git Bash, desde la raíz del proyecto):
--   docker compose exec -T postgres psql -U pp -d primerplayer -v ON_ERROR_STOP=1 < scripts/db/01-limpiar-base.sql
-- =====================================================================================================

BEGIN;

DO $$
DECLARE
  v_admin_email CONSTANT text := 'admin@primerplayer.cl';
  v_tablas text;
  v_max_id bigint;
BEGIN
  IF current_database() NOT IN ('primerplayer', 'runagard_staging') THEN
    RAISE EXCEPTION 'Este script solo se ejecuta en local ("primerplayer") o staging ("runagard_staging"); estás en "%".',
      current_database();
  END IF;

  -- Sin administrador nadie podría volver a entrar al panel: mejor no borrar nada.
  -- En staging se permite: poblar-staging.sh corre 02-poblar-base.sql justo después, y ese crea el administrador.
  IF current_database() = 'primerplayer' AND NOT EXISTS (SELECT 1 FROM usuario WHERE email = v_admin_email) THEN
    RAISE EXCEPTION 'No existe % en la base; no se limpia nada para no dejarte sin acceso.', v_admin_email;
  END IF;

  -- 1) Todas las tablas salvo el historial de Flyway y la de usuarios (incluye tablas que se creen en el futuro).
  SELECT string_agg(format('%I.%I', schemaname, tablename), ', ' ORDER BY tablename)
    INTO v_tablas
    FROM pg_tables
   WHERE schemaname = 'public'
     AND tablename NOT IN ('flyway_schema_history', 'usuario');

  IF v_tablas IS NOT NULL THEN
    EXECUTE 'TRUNCATE TABLE ' || v_tablas || ' RESTART IDENTITY CASCADE';
    RAISE NOTICE 'Tablas vaciadas: %', v_tablas;
  END IF;

  -- 2) Usuarios: queda solo el administrador, desbloqueado y activo.
  DELETE FROM usuario WHERE email <> v_admin_email;
  UPDATE usuario
     SET activo = TRUE, eliminado_en = NULL, intentos_fallidos = 0, bloqueado_hasta = NULL
   WHERE email = v_admin_email;

  -- El próximo usuario nuevo toma el ID siguiente al del administrador.
  SELECT max(id) INTO v_max_id FROM usuario;
  EXECUTE format('ALTER TABLE usuario ALTER COLUMN id RESTART WITH %s', coalesce(v_max_id, 0) + 1);

  RAISE NOTICE 'Usuarios: solo queda % (id %).', v_admin_email, v_max_id;
END $$;

-- Los códigos de pedido (PP-AAAA-NNNNNN) vuelven a empezar en 1.
ALTER SEQUENCE pedido_codigo_seq RESTART WITH 1;

COMMIT;
