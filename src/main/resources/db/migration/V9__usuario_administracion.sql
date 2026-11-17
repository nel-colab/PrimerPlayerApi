-- Administración de usuarios (contrato del frontend docs/api-admin-usuarios.md). Solo expansión: columnas nuevas
-- con valores por defecto, compatibles con el código anterior.

ALTER TABLE usuario
  -- Baneo: no puede iniciar sesión ni refrescar. hasta NULL = permanente. Distinto de bloqueado_hasta
  -- (bloqueo automático por intentos fallidos).
  ADD COLUMN baneo_motivo              VARCHAR(300),
  ADD COLUMN baneo_hasta               TIMESTAMPTZ,
  ADD COLUMN baneo_creado_en           TIMESTAMPTZ,
  -- Lista negra: puede iniciar sesión, pero no comprar ni solicitar cumpleaños.
  ADD COLUMN lista_negra_motivo        VARCHAR(300),
  ADD COLUMN lista_negra_en            TIMESTAMPTZ,
  -- El administrador pidió un cambio de contraseña: el siguiente ingreso debe cambiarla.
  ADD COLUMN cambio_password_pendiente BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN ultimo_acceso_en          TIMESTAMPTZ;

-- Un baneo tiene motivo y fecha de creación; sin motivo no hay baneo (ni fecha).
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_baneo
  CHECK ((baneo_motivo IS NULL) = (baneo_creado_en IS NULL) AND (baneo_motivo IS NOT NULL OR baneo_hasta IS NULL));
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_lista_negra
  CHECK ((lista_negra_motivo IS NULL) = (lista_negra_en IS NULL));

-- Panel de usuarios: más recientes primero (excluye el cliente anónimo de mostrador y los eliminados).
CREATE INDEX ix_usuario_panel ON usuario (creado_en DESC) WHERE NOT es_anonimo AND eliminado_en IS NULL;
