-- Eventos: tipo 'lanzamiento', hora de término, cumpleaños sin cupos y solicitudes de cumpleaños.
-- Todos los cambios son de expansión (compatibles con el código anterior).

-- 1) Nuevo tipo 'lanzamiento' (ediciones nuevas de cartas y juegos de mesa). 'prelanzamiento' se mantiene.
ALTER TABLE evento DROP CONSTRAINT evento_tipo_check;
ALTER TABLE evento ADD CONSTRAINT evento_tipo_check CHECK (tipo IN
  ('league-cup','league-challenge','torneo-tcg','prelanzamiento','lanzamiento','videojuegos','cumpleanos','otros'));

-- 2) Hora de término: obligatoria para cumpleaños (bloque horario reservado), opcional para el resto.
ALTER TABLE evento ADD COLUMN fecha_hora_fin TIMESTAMPTZ;
ALTER TABLE evento ADD CONSTRAINT ck_evento_fin CHECK (fecha_hora_fin IS NULL OR fecha_hora_fin > fecha_hora);
ALTER TABLE evento ADD CONSTRAINT ck_evento_cumpleanos_fin CHECK (tipo <> 'cumpleanos' OR fecha_hora_fin IS NOT NULL);

-- 3) Un cumpleaños no vende cupos: cupos_totales NULL solo y siempre para 'cumpleanos'.
--    (CHECK cupos_totales > 0 y ck_evento_cupos siguen valiendo: con NULL no se evalúan como falsos.)
ALTER TABLE evento ALTER COLUMN cupos_totales DROP NOT NULL;
ALTER TABLE evento ADD CONSTRAINT ck_evento_cupos_por_tipo CHECK ((tipo = 'cumpleanos') = (cupos_totales IS NULL));

-- 4) Red final: dos cumpleaños vigentes nunca se traslapan (la tienda cierra en ese bloque).
--    tstzrange es [inicio, fin): un bloque puede empezar justo cuando termina otro.
ALTER TABLE evento ADD CONSTRAINT ex_evento_cumpleanos_traslape
  EXCLUDE USING gist (tstzrange(fecha_hora, fecha_hora_fin) WITH &&)
  WHERE (tipo = 'cumpleanos' AND eliminado_en IS NULL);

-- Agenda: búsqueda de traslapes por rango de fechas entre eventos vigentes.
CREATE INDEX ix_evento_agenda ON evento (fecha_hora) WHERE eliminado_en IS NULL;

-- 5) Solicitudes de cumpleaños: el cliente pide un bloque, la tienda aprueba (crea el evento) o rechaza.
CREATE TABLE solicitud_cumpleanos (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  cliente_id      BIGINT       NOT NULL REFERENCES usuario(id),
  inicio          TIMESTAMPTZ  NOT NULL,
  fin             TIMESTAMPTZ  NOT NULL,
  festejado       VARCHAR(80)  NOT NULL,
  invitados       SMALLINT     NOT NULL CHECK (invitados BETWEEN 1 AND 200),
  telefono        VARCHAR(20)  NOT NULL,
  comentario      VARCHAR(500),
  estado          VARCHAR(10)  NOT NULL CHECK (estado IN ('PENDIENTE','APROBADA','RECHAZADA','CANCELADA')),
  motivo_rechazo  VARCHAR(300),
  evento_id       BIGINT REFERENCES evento(id),
  resuelta_por    BIGINT REFERENCES usuario(id),
  resuelta_en     TIMESTAMPTZ,
  creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  actualizado_en  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT ck_solicitud_bloque CHECK (fin > inicio),
  -- Aprobada <=> tiene su evento; rechazada => tiene motivo
  CONSTRAINT ck_solicitud_evento CHECK ((estado = 'APROBADA') = (evento_id IS NOT NULL)),
  CONSTRAINT ck_solicitud_rechazo CHECK (estado <> 'RECHAZADA' OR motivo_rechazo IS NOT NULL)
);
-- "Mis solicitudes" del cliente, más recientes primero
CREATE INDEX ix_solicitud_cliente ON solicitud_cumpleanos (cliente_id, creado_en DESC);
-- Bandeja de la tienda: pendientes por fecha solicitada
CREATE INDEX ix_solicitud_pendiente ON solicitud_cumpleanos (inicio) WHERE estado = 'PENDIENTE';
-- Una solicitud aprobada genera exactamente un evento
CREATE UNIQUE INDEX uq_solicitud_evento ON solicitud_cumpleanos (evento_id) WHERE evento_id IS NOT NULL;
