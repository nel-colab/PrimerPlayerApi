-- Aprobación de productos: lo que crea o cambia un VENDEDOR no llega a la tienda hasta que un ADMIN lo autoriza.
--
-- producto.aprobacion es el estado del alta: un producto que nunca se aprobó no puede estar publicado (CHECK).
-- Los productos existentes ya estaban a la venta: quedan APROBADO (DEFAULT).
ALTER TABLE producto
  ADD COLUMN aprobacion VARCHAR(9) NOT NULL DEFAULT 'APROBADO'
    CHECK (aprobacion IN ('PENDIENTE','APROBADO','RECHAZADO'));
ALTER TABLE producto
  ADD CONSTRAINT ck_producto_publicado_aprobado CHECK (aprobacion = 'APROBADO' OR NOT publicado);

-- Cada solicitud de un VENDEDOR: ALTA (producto nuevo) o EDICION (cambio sobre un producto ya aprobado).
-- En una EDICION el producto sigue a la venta con lo aprobado; lo propuesto vive en datos_propuestos (JSON de
-- DatosProducto) y solo se copia al producto cuando el ADMIN aprueba. version_base = versión del producto sobre la
-- que se propuso el cambio: si el producto cambió después, la propuesta está desactualizada y no se aplica.
CREATE TABLE producto_revision (
  id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  producto_id       BIGINT       NOT NULL REFERENCES producto(id),
  tipo              VARCHAR(7)   NOT NULL CHECK (tipo IN ('ALTA','EDICION')),
  estado            VARCHAR(10)  NOT NULL DEFAULT 'PENDIENTE'
                      CHECK (estado IN ('PENDIENTE','APROBADA','RECHAZADA','DESCARTADA')),
  datos_propuestos  JSONB,
  version_base      INTEGER      NOT NULL,
  motivo_rechazo    VARCHAR(500),
  solicitado_por    BIGINT       NOT NULL REFERENCES usuario(id),
  solicitado_en     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  revisado_por      BIGINT       REFERENCES usuario(id),
  revisado_en       TIMESTAMPTZ,
  CHECK ((tipo = 'EDICION') = (datos_propuestos IS NOT NULL)),
  CHECK (estado <> 'RECHAZADA' OR motivo_rechazo IS NOT NULL),
  CHECK ((estado = 'PENDIENTE') = (revisado_en IS NULL))
);
-- A lo más una solicitud pendiente por producto: una nueva edición reemplaza la propuesta pendiente.
CREATE UNIQUE INDEX uq_producto_revision_pendiente ON producto_revision (producto_id) WHERE estado = 'PENDIENTE';
-- Bandeja del ADMIN: por estado, las más antiguas primero
CREATE INDEX ix_producto_revision_bandeja ON producto_revision (estado, solicitado_en);
-- Historial de un producto
CREATE INDEX ix_producto_revision_producto ON producto_revision (producto_id, solicitado_en DESC);
