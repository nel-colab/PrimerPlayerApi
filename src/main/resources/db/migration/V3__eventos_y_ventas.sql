CREATE TABLE evento (
  id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  titulo             VARCHAR(160) NOT NULL,
  tipo               VARCHAR(20)  NOT NULL CHECK (tipo IN ('league-cup','league-challenge','torneo-tcg',
                                                          'prelanzamiento','videojuegos','cumpleanos','otros')),
  fecha_hora         TIMESTAMPTZ  NOT NULL,
  lugar              VARCHAR(160) NOT NULL,
  precio             BIGINT       NOT NULL CHECK (precio >= 0),
  cupos_totales      INTEGER      NOT NULL CHECK (cupos_totales > 0),
  cupos_ocupados     INTEGER      NOT NULL DEFAULT 0 CHECK (cupos_ocupados >= 0),
  limite_por_cliente INTEGER      CHECK (limite_por_cliente IS NULL OR limite_por_cliente > 0),
  descripcion        TEXT         NOT NULL,
  consideraciones    TEXT[]       NOT NULL DEFAULT '{}',
  imagen_archivo_id  UUID REFERENCES archivo(id),
  imagen_url         VARCHAR(500) CHECK (imagen_url ~ '^https://'),
  publicado          BOOLEAN      NOT NULL DEFAULT FALSE,
  version            INTEGER      NOT NULL DEFAULT 0,
  creado_en          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  actualizado_en     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  eliminado_en       TIMESTAMPTZ,
  CONSTRAINT ck_evento_cupos CHECK (cupos_ocupados <= cupos_totales)
);
CREATE INDEX ix_evento_publico ON evento (fecha_hora) WHERE publicado AND eliminado_en IS NULL;

CREATE SEQUENCE pedido_codigo_seq START 1;

CREATE TABLE pedido (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  codigo           VARCHAR(20)  NOT NULL UNIQUE,     -- PP-2026-000123
  cliente_id       BIGINT       NOT NULL REFERENCES usuario(id),
  vendedor_id      BIGINT REFERENCES usuario(id),
  canal            VARCHAR(6)   NOT NULL CHECK (canal IN ('web','tienda')),
  estado           VARCHAR(16)  NOT NULL CHECK (estado IN ('PENDIENTE_PAGO','PAGADO','LISTO_RETIRO',
                                    'ENTREGADO','RECHAZADO','EXPIRADO','ANULADO','REEMBOLSADO')),
  total            BIGINT       NOT NULL CHECK (total >= 0),
  idempotency_key  UUID,
  idempotency_hash CHAR(64),                          -- SHA-256 del cuerpo original
  expira_en        TIMESTAMPTZ,                       -- solo web en PENDIENTE_PAGO
  pagado_en        TIMESTAMPTZ,
  creado_en        TIMESTAMPTZ  NOT NULL DEFAULT now(),
  actualizado_en   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT ck_pedido_vendedor CHECK ((canal = 'tienda') = (vendedor_id IS NOT NULL)),
  CONSTRAINT uq_pedido_idempotencia UNIQUE (cliente_id, idempotency_key)
);
CREATE INDEX ix_pedido_cliente     ON pedido (cliente_id, creado_en DESC);
CREATE INDEX ix_pedido_dia         ON pedido (creado_en) WHERE estado IN ('PAGADO','LISTO_RETIRO','ENTREGADO');
CREATE INDEX ix_pedido_expiracion  ON pedido (expira_en) WHERE estado = 'PENDIENTE_PAGO';

CREATE TABLE pedido_item (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  pedido_id        BIGINT       NOT NULL REFERENCES pedido(id),
  tipo             VARCHAR(7)   NOT NULL CHECK (tipo IN ('product','event')),
  producto_id      BIGINT REFERENCES producto(id),
  evento_id        BIGINT REFERENCES evento(id),
  nombre           VARCHAR(200) NOT NULL,             -- snapshot
  cantidad         INTEGER      NOT NULL CHECK (cantidad > 0),
  precio_unitario  BIGINT       NOT NULL CHECK (precio_unitario >= 0),  -- snapshot
  subtotal         BIGINT GENERATED ALWAYS AS (cantidad * precio_unitario) STORED,
  CHECK ((tipo = 'product' AND producto_id IS NOT NULL AND evento_id IS NULL)
      OR (tipo = 'event'   AND evento_id   IS NOT NULL AND producto_id IS NULL))
);
CREATE INDEX ix_item_pedido   ON pedido_item (pedido_id);
CREATE INDEX ix_item_producto ON pedido_item (producto_id) WHERE producto_id IS NOT NULL;  -- límite acumulado, top 5
CREATE INDEX ix_item_evento   ON pedido_item (evento_id)   WHERE evento_id   IS NOT NULL;

CREATE TABLE inscripcion (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  evento_id        BIGINT       NOT NULL REFERENCES evento(id),
  cliente_id       BIGINT       NOT NULL REFERENCES usuario(id),
  pedido_id        BIGINT       NOT NULL REFERENCES pedido(id),
  cantidad         INTEGER      NOT NULL CHECK (cantidad > 0),
  estado           VARCHAR(10)  NOT NULL CHECK (estado IN ('RESERVADA','CONFIRMADA','CANCELADA','EXPIRADA')),
  titulo_snapshot  VARCHAR(160) NOT NULL,
  fecha_snapshot   TIMESTAMPTZ  NOT NULL,
  lugar_snapshot   VARCHAR(160) NOT NULL,
  precio_snapshot  BIGINT       NOT NULL,
  creado_en        TIMESTAMPTZ  NOT NULL DEFAULT now(),
  cancelada_en     TIMESTAMPTZ
);
CREATE INDEX ix_inscripcion_cliente ON inscripcion (cliente_id, evento_id) WHERE estado IN ('RESERVADA','CONFIRMADA');
CREATE INDEX ix_inscripcion_evento  ON inscripcion (evento_id)             WHERE estado IN ('RESERVADA','CONFIRMADA');
