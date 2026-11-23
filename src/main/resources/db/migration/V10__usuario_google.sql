-- Inicio de sesión y verificación de correo con Google. Solo expansión: columna nullable, compatible con el código
-- anterior. Una cuenta creada con Google no tiene contraseña (password_hash NULL) hasta que la defina con el enlace
-- de recuperación.

ALTER TABLE usuario
  -- "sub" del ID token de Google: identificador estable de la cuenta de Google (el correo puede cambiar).
  ADD COLUMN google_sub VARCHAR(255);

-- Una cuenta de Google se vincula a un solo usuario; además es la búsqueda del inicio de sesión con Google.
CREATE UNIQUE INDEX uq_usuario_google_sub ON usuario (google_sub) WHERE google_sub IS NOT NULL;
