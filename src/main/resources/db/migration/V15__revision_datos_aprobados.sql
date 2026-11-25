-- "Aprobar con cambios": el ADMIN puede corregir lo que propuso el vendedor antes de aprobarlo. datos_propuestos
-- conserva lo que envió el vendedor y datos_aprobados lo que finalmente aprobó el ADMIN (mismo formato JSON).
-- NULL = se aprobó tal cual (o la revisión no está aprobada).
ALTER TABLE producto_revision ADD COLUMN datos_aprobados JSONB;
ALTER TABLE producto_revision
  ADD CONSTRAINT ck_producto_revision_aprobados CHECK (datos_aprobados IS NULL OR estado = 'APROBADA');
