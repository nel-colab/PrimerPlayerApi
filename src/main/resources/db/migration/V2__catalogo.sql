CREATE TABLE categoria (
  id         SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  slug       VARCHAR(60)  NOT NULL UNIQUE,
  titulo     VARCHAR(80)  NOT NULL,
  parent_id  SMALLINT REFERENCES categoria(id),
  es_hoja    BOOLEAN      NOT NULL,
  orden      SMALLINT     NOT NULL DEFAULT 0
);

CREATE TABLE franquicia (
  id      SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre  VARCHAR(60) NOT NULL UNIQUE
);

CREATE TABLE archivo (
  id           UUID PRIMARY KEY,
  bucket       VARCHAR(30)  NOT NULL CHECK (bucket IN ('public-media','private-docs')),
  clave        VARCHAR(200) NOT NULL UNIQUE,
  mime         VARCHAR(40)  NOT NULL CHECK (mime IN ('image/webp','image/jpeg','image/png')),
  bytes        INTEGER      NOT NULL CHECK (bytes > 0),
  ancho        INTEGER,
  alto         INTEGER,
  sha256       CHAR(64)     NOT NULL,
  subido_por   BIGINT REFERENCES usuario(id),
  creado_en    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE producto (
  id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre             VARCHAR(200) NOT NULL,
  descripcion        TEXT,
  precio             BIGINT       NOT NULL CHECK (precio >= 0),
  categoria_id       SMALLINT     NOT NULL REFERENCES categoria(id),
  tipo               VARCHAR(40),
  idioma             VARCHAR(30),
  marca              VARCHAR(60),
  plataforma         VARCHAR(30),
  franquicia_id      SMALLINT REFERENCES franquicia(id),
  linea              VARCHAR(80),
  codigo_barras      VARCHAR(14)  CHECK (codigo_barras ~ '^([0-9]{8}|[0-9]{12}|[0-9]{13})$'),
  limite_por_cliente INTEGER      CHECK (limite_por_cliente IS NULL OR limite_por_cliente > 0),
  stock              INTEGER      NOT NULL DEFAULT 0 CHECK (stock >= 0),
  popularidad        INTEGER      NOT NULL DEFAULT 0,
  fecha_ingreso      DATE         NOT NULL DEFAULT CURRENT_DATE,
  publicado          BOOLEAN      NOT NULL DEFAULT FALSE,
  version            INTEGER      NOT NULL DEFAULT 0,      -- @Version para el mantenedor
  creado_en          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  actualizado_en     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  eliminado_en       TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_producto_codigo_barras ON producto (codigo_barras) WHERE codigo_barras IS NOT NULL;
-- Catálogo público: filtro por categoría + publicado + orden
CREATE INDEX ix_producto_publico ON producto (categoria_id, popularidad DESC)
  WHERE publicado AND eliminado_en IS NULL;
CREATE INDEX ix_producto_precio  ON producto (precio) WHERE publicado AND eliminado_en IS NULL;
CREATE INDEX ix_producto_fecha   ON producto (fecha_ingreso DESC) WHERE publicado AND eliminado_en IS NULL;
-- Búsqueda por nombre con similitud (pg_trgm) sin acentos
CREATE OR REPLACE FUNCTION f_unaccent(text) RETURNS text
  LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$ SELECT public.unaccent('public.unaccent', $1) $$;
CREATE INDEX ix_producto_nombre_trgm ON producto USING gin (f_unaccent(lower(nombre)) gin_trgm_ops);
-- Mantenedor: stock bajo y agotados
CREATE INDEX ix_producto_stock ON producto (stock) WHERE eliminado_en IS NULL;

CREATE TABLE producto_imagen (
  producto_id  BIGINT   NOT NULL REFERENCES producto(id) ON DELETE CASCADE,
  posicion     SMALLINT NOT NULL CHECK (posicion BETWEEN 0 AND 4),   -- máx. 5
  archivo_id   UUID REFERENCES archivo(id),
  url_externa  VARCHAR(500) CHECK (url_externa ~ '^https://'),
  es_portada   BOOLEAN  NOT NULL DEFAULT FALSE,
  PRIMARY KEY (producto_id, posicion),
  CHECK ((archivo_id IS NULL) <> (url_externa IS NULL))
);
CREATE UNIQUE INDEX uq_producto_portada ON producto_imagen (producto_id) WHERE es_portada;

CREATE TABLE movimiento_stock (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  producto_id      BIGINT      NOT NULL REFERENCES producto(id),
  tipo             VARCHAR(20) NOT NULL CHECK (tipo IN
                     ('VENTA_WEB','VENTA_MOSTRADOR','AJUSTE','ANULACION','RESERVA_EXPIRADA','INGRESO')),
  cantidad         INTEGER     NOT NULL CHECK (cantidad <> 0),   -- negativa = salida
  stock_resultante INTEGER     NOT NULL CHECK (stock_resultante >= 0),
  referencia       VARCHAR(40),                                   -- código de pedido
  motivo           VARCHAR(200),
  usuario_id       BIGINT REFERENCES usuario(id),
  creado_en        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_mov_producto ON movimiento_stock (producto_id, creado_en DESC);

CREATE TABLE favorito (
  usuario_id  BIGINT NOT NULL REFERENCES usuario(id),
  producto_id BIGINT NOT NULL REFERENCES producto(id),
  creado_en   TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (usuario_id, producto_id)
);

CREATE TABLE alerta_stock (
  usuario_id    BIGINT NOT NULL REFERENCES usuario(id),
  producto_id   BIGINT NOT NULL REFERENCES producto(id),
  creado_en     TIMESTAMPTZ NOT NULL DEFAULT now(),
  notificado_en TIMESTAMPTZ,
  PRIMARY KEY (usuario_id, producto_id)
);
CREATE INDEX ix_alerta_pendiente ON alerta_stock (producto_id) WHERE notificado_en IS NULL;

CREATE TABLE config_inventario (
  id                  SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),   -- fila única
  umbral_stock_bajo   INTEGER  NOT NULL DEFAULT 3 CHECK (umbral_stock_bajo >= 0)
);
INSERT INTO config_inventario (id) VALUES (1);
