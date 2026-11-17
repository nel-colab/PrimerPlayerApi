CREATE TABLE cierre_caja (
  fecha             DATE          PRIMARY KEY,          -- día local America/Santiago: uno por día
  fondo_inicial     BIGINT        NOT NULL CHECK (fondo_inicial >= 0),
  efectivo_contado  BIGINT        NOT NULL CHECK (efectivo_contado >= 0),
  efectivo_neto     BIGINT        NOT NULL,             -- calculado en servidor
  esperado_en_caja  BIGINT        NOT NULL,             -- fondo + efectivo_neto
  total_ventas      BIGINT        NOT NULL,
  numero_ventas     INTEGER       NOT NULL,
  diferencia        BIGINT        NOT NULL,             -- contado − esperado
  subtotales        JSONB         NOT NULL,             -- {"efectivo": 120000, "debito": ...}
  observaciones     VARCHAR(1000) NOT NULL DEFAULT '',
  version           INTEGER       NOT NULL DEFAULT 1,
  cerrado_por       BIGINT        NOT NULL REFERENCES usuario(id),
  cerrado_en        TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE cierre_caja_version (            -- historial: cada reemplazo guarda la versión anterior
  fecha        DATE        NOT NULL,
  version      INTEGER     NOT NULL,
  datos        JSONB       NOT NULL,
  cerrado_por  BIGINT      NOT NULL REFERENCES usuario(id),
  cerrado_en   TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (fecha, version)
);

CREATE TABLE auditoria (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  ocurrido_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
  usuario_id   BIGINT REFERENCES usuario(id),
  accion       VARCHAR(40) NOT NULL,              -- PRECIO_CAMBIADO, LIMITE_CAMBIADO, ROL_CAMBIADO...
  entidad      VARCHAR(30) NOT NULL,
  entidad_id   VARCHAR(40) NOT NULL,
  antes        JSONB,
  despues      JSONB,
  ip           INET,
  request_id   VARCHAR(40)
);
CREATE INDEX ix_auditoria_entidad ON auditoria (entidad, entidad_id, ocurrido_en DESC);
CREATE INDEX ix_auditoria_fecha   ON auditoria (ocurrido_en DESC);

CREATE TABLE correo_enviado (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  destino      CITEXT       NOT NULL,
  plantilla    VARCHAR(40)  NOT NULL,
  referencia   VARCHAR(60),
  enviado_en   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  proveedor_id VARCHAR(100),
  UNIQUE (plantilla, referencia, destino)          -- evita duplicar el mismo correo al reintentar
);

-- ShedLock (jobs programados sin duplicarse entre instancias)
CREATE TABLE shedlock (
  name       VARCHAR(64)  PRIMARY KEY,
  lock_until TIMESTAMPTZ  NOT NULL,
  locked_at  TIMESTAMPTZ  NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);
-- La tabla event_publication de Spring Modulith (outbox) la crea Modulith
-- (spring.modulith.events.jdbc.schema-initialization.enabled=true) o una migración propia.
