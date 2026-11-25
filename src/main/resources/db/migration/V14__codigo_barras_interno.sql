-- Correlativo de los códigos de barras internos (EAN-13 '200' + 9 dígitos + verificador) para productos sin código
-- de fábrica. Una secuencia: dos vendedores que generan un código al mismo tiempo nunca reciben el mismo.
-- Parte en 1000 porque los datos de demostración (scripts/db/02-poblar-base.sql) ya usan los primeros números; de
-- todas formas la API se salta cualquier código que ya esté en uso.
CREATE SEQUENCE producto_codigo_interno_seq START WITH 1000 MAXVALUE 999999999 NO CYCLE;
