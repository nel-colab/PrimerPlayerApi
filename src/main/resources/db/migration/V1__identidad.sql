CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE TABLE usuario (
  id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  email               CITEXT       NOT NULL,
  nombre              VARCHAR(120) NOT NULL,
  password_hash       VARCHAR(100),                 -- NULL para el cliente anónimo de mostrador
  rol                 VARCHAR(10)  NOT NULL CHECK (rol IN ('CLIENTE','VENDEDOR','ADMIN')),
  activo              BOOLEAN      NOT NULL DEFAULT TRUE,
  es_anonimo          BOOLEAN      NOT NULL DEFAULT FALSE,
  email_verificado_en TIMESTAMPTZ,
  telefono            VARCHAR(20),
  rut                 VARCHAR(12),
  intentos_fallidos   SMALLINT     NOT NULL DEFAULT 0,
  bloqueado_hasta     TIMESTAMPTZ,
  creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  actualizado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  eliminado_en        TIMESTAMPTZ,
  CONSTRAINT uq_usuario_email UNIQUE (email)
);

CREATE TABLE refresh_token (
  id              UUID PRIMARY KEY,
  usuario_id      BIGINT      NOT NULL REFERENCES usuario(id),
  familia         UUID        NOT NULL,              -- cadena de rotación; reuso => revocar familia
  token_hash      CHAR(64)    NOT NULL UNIQUE,       -- SHA-256 del token opaco
  expira_en       TIMESTAMPTZ NOT NULL,
  revocado_en     TIMESTAMPTZ,
  reemplazado_por UUID,
  creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
  ip              INET,
  user_agent      VARCHAR(255)
);
CREATE INDEX ix_refresh_usuario ON refresh_token (usuario_id) WHERE revocado_en IS NULL;

CREATE TABLE token_un_uso (
  id          UUID PRIMARY KEY,
  usuario_id  BIGINT      NOT NULL REFERENCES usuario(id),
  tipo        VARCHAR(20) NOT NULL CHECK (tipo IN ('VERIFICACION','RECUPERACION')),
  token_hash  CHAR(64)    NOT NULL UNIQUE,
  expira_en   TIMESTAMPTZ NOT NULL,
  usado_en    TIMESTAMPTZ
);

CREATE TABLE direccion (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  usuario_id  BIGINT       NOT NULL REFERENCES usuario(id),
  alias       VARCHAR(40)  NOT NULL,
  calle       VARCHAR(160) NOT NULL,
  comuna      VARCHAR(80)  NOT NULL,
  region      VARCHAR(80)  NOT NULL,
  referencia  VARCHAR(200),
  principal   BOOLEAN      NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uq_direccion_principal ON direccion (usuario_id) WHERE principal;
