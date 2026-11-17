CREATE TABLE pago (
  id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  pedido_id              BIGINT      NOT NULL REFERENCES pedido(id),
  metodo                 VARCHAR(16) NOT NULL CHECK (metodo IN ('efectivo','debito','credito','prepago',
                           'transferencia','cartas-tcg','nota-credito','tarjeta-credito','webpay')),
  monto                  BIGINT      NOT NULL CHECK (monto > 0),
  numero_boleta          VARCHAR(30),
  comprobante_archivo_id UUID REFERENCES archivo(id),
  carta_nombre           VARCHAR(120),
  carta_codigo           VARCHAR(40),
  carta_valor            BIGINT CHECK (carta_valor > 0),
  saldo_a_favor          BIGINT NOT NULL DEFAULT 0 CHECK (saldo_a_favor >= 0),
  efectivo_recibido      BIGINT CHECK (efectivo_recibido >= 0),
  vuelto_entregado       BIGINT CHECK (vuelto_entregado >= 0),
  creado_en              TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (metodo NOT IN ('debito','credito','prepago') OR numero_boleta IS NOT NULL),
  CHECK (metodo <> 'transferencia' OR comprobante_archivo_id IS NOT NULL),
  CHECK (metodo <> 'cartas-tcg' OR (carta_nombre IS NOT NULL AND carta_codigo IS NOT NULL AND carta_valor IS NOT NULL)),
  CHECK (metodo <> 'efectivo' OR (efectivo_recibido IS NOT NULL AND vuelto_entregado IS NOT NULL
                                  AND efectivo_recibido >= monto))
);
CREATE INDEX ix_pago_pedido ON pago (pedido_id);

-- La suma de pagos = total se valida en el servicio y, como red final, con un trigger diferido:
CREATE OR REPLACE FUNCTION f_pagos_cuadran() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF (SELECT COALESCE(SUM(monto),0) FROM pago WHERE pedido_id = NEW.pedido_id)
     <> (SELECT total FROM pedido WHERE id = NEW.pedido_id) THEN
    RAISE EXCEPTION 'pagos no cuadran con el total del pedido %', NEW.pedido_id USING ERRCODE = '23514';
  END IF;
  RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER tg_pagos_cuadran AFTER INSERT ON pago
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION f_pagos_cuadran();

CREATE TABLE transaccion_webpay (
  id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  pedido_id            BIGINT       NOT NULL REFERENCES pedido(id),
  buy_order            VARCHAR(26)  NOT NULL UNIQUE,    -- máx. 26 caracteres en Webpay
  session_id           VARCHAR(61)  NOT NULL,
  token                VARCHAR(64)  UNIQUE,
  monto                BIGINT       NOT NULL CHECK (monto > 0),
  estado               VARCHAR(16)  NOT NULL CHECK (estado IN ('CREADA','AUTORIZADA','RECHAZADA',
                                     'ANULADA_USUARIO','EXPIRADA','ERROR','REVERSADA','ANULADA')),
  response_code        SMALLINT,
  authorization_code   VARCHAR(10),
  payment_type_code    VARCHAR(4),                      -- VD, VN, VC, SI, S2, NC, VP
  installments_number  SMALLINT,
  card_last4           CHAR(4),
  accounting_date      CHAR(4),                         -- MMDD
  transaction_date     TIMESTAMPTZ,
  respuesta_commit     JSONB,
  respuesta_reembolso  JSONB,
  creado_en            TIMESTAMPTZ  NOT NULL DEFAULT now(),
  confirmado_en        TIMESTAMPTZ
);
CREATE INDEX ix_webpay_pedido ON transaccion_webpay (pedido_id);
