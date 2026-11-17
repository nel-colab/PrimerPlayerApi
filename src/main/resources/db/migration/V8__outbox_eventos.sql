-- Outbox de eventos de Spring Modulith (registro de publicaciones, JPA, Modulith 2.1).
-- Cada evento que tiene escuchas (@ApplicationModuleListener / @TransactionalEventListener) se guarda aquí en la misma
-- transacción que lo publica; se marca completado cuando el escucha termina. Si la app se cae antes, se reintenta al
-- volver: así un correo de verificación o una alerta de stock no se pierden.
-- Columnas = mapeo de org.springframework.modulith.events.jpa.JpaEventPublication (nombres en snake_case).

CREATE TABLE event_publication (
  id                     UUID         PRIMARY KEY,
  listener_id            TEXT         NOT NULL,
  event_type             TEXT         NOT NULL,
  serialized_event       TEXT         NOT NULL,
  publication_date       TIMESTAMPTZ  NOT NULL,
  completion_date        TIMESTAMPTZ,
  status                 TEXT,
  completion_attempts    INTEGER      NOT NULL DEFAULT 0,
  last_resubmission_date TIMESTAMPTZ
);
-- Publicaciones pendientes (las que se reintentan) y búsqueda del evento serializado al completarlo.
CREATE INDEX ix_event_publication_pendientes ON event_publication (publication_date) WHERE completion_date IS NULL;
CREATE INDEX ix_event_publication_evento     ON event_publication USING hash (serialized_event);

-- Solo se usa si se activa el modo "archive" de Modulith (completadas se mueven aquí). Se crea para no fallar si se activa.
CREATE TABLE event_publication_archive (
  id                     UUID         PRIMARY KEY,
  listener_id            TEXT         NOT NULL,
  event_type             TEXT         NOT NULL,
  serialized_event       TEXT         NOT NULL,
  publication_date       TIMESTAMPTZ  NOT NULL,
  completion_date        TIMESTAMPTZ,
  status                 TEXT,
  completion_attempts    INTEGER      NOT NULL DEFAULT 0,
  last_resubmission_date TIMESTAMPTZ
);
