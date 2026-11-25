-- Verificar el correo y restablecer la contraseña pasan de enlace (token en la URL) a código de 6 dígitos: los correos
-- ya no llevan URLs. token_hash guarda SHA-256(id || ':' || código); el id del token hace de sal, así dos códigos
-- iguales nunca chocan con el UNIQUE.
--
-- intentos: códigos errados contra este token (al 5º se vence). creado_en: para el tope de intentos por hora de una
-- cuenta, que impide adivinar pidiendo códigos nuevos una y otra vez.
ALTER TABLE token_un_uso ADD COLUMN intentos  SMALLINT    NOT NULL DEFAULT 0 CHECK (intentos >= 0);
ALTER TABLE token_un_uso ADD COLUMN creado_en TIMESTAMPTZ NOT NULL DEFAULT now();

-- Los enlaces ya enviados dejan de servir (la web ya no tiene la página que los recibe): se piden de nuevo.
UPDATE token_un_uso SET expira_en = now() WHERE usado_en IS NULL AND expira_en > now();

-- Código pendiente más reciente de una cuenta y suma de intentos de la última hora
CREATE INDEX ix_token_un_uso_usuario ON token_un_uso (usuario_id, tipo, creado_en DESC);
