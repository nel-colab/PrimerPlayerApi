# Reglas de negocio (el servidor es la fuente de verdad)

## Roles

| Rol | Acceso |
| --- | --- |
| `CLIENTE` (frontend: `usuario`) | Tienda pública y su perfil (`/me/**`). Solo ve sus propios pedidos e inscripciones. |
| `VENDEDOR` | Panel, stock, eventos, POS y cierre de caja. **No** accede a reportes ni estadísticas. |
| `ADMIN` | Todo lo anterior + reportes, usuarios internos, auditoría, reembolsos, anulación de ventas POS y cambio de límites ya configurados. |

- **Todos los roles compran**: VENDEDOR y ADMIN también usan `/me/**` y `POST /checkout` como cualquier cliente
  (solo ven lo propio y acumulan su límite por cliente).
- **Google**: el servidor valida el ID token (firma con las llaves de Google, `iss`, `aud` = client id, vigencia) y
  exige `email_verified`. Google prueba que el correo es del dueño: vincula la cuenta existente con ese correo
  (`usuario.google_sub`) y la marca verificada; si no existe crea un CLIENTE sin contraseña. Un `google_sub` por usuario.
- **Correo verificado del personal**: VENDEDOR/ADMIN sin correo verificado reciben 403 `email-not-verified` en
  `/admin/**`; lo verifican con el enlace o con Google (`POST /me/verificacion/google`). Para comprar ya se exigía.
- **Perfil completo del personal**: VENDEDOR/ADMIN sin nombre, teléfono o RUT reciben 403 `profile-incomplete` en
  `/admin/**` (se revisa en la base en cada request). Lo completan en `PUT /me/perfil`; `GET /me` expone
  `profileComplete`. Comprar no exige perfil completo.

## Stock

1. Nunca negativo. `CHECK (stock >= 0)` en la base.
2. Se descuenta de forma atómica en la misma transacción que crea el pedido:
   `UPDATE producto SET stock = stock - :q WHERE id = :id AND eliminado_en IS NULL AND stock >= :q`.
   0 filas afectadas → error `out-of-stock` (stock = 0) o `stock-limit` (stock < q).
3. Procesar líneas ordenadas por `producto_id` (y luego `evento_id`) para evitar interbloqueos.
4. Todo movimiento queda en el kardex `movimiento_stock` con tipo: `VENTA_WEB`, `VENTA_MOSTRADOR`, `AJUSTE`,
   `ANULACION`, `RESERVA_EXPIRADA`, `INGRESO`, y `stock_resultante` (vía `RETURNING`).
5. Ajuste manual (+/−) exige motivo: `UPDATE ... SET stock = stock + :delta WHERE ... AND stock + :delta >= 0`.
6. Cuando el stock pasa de 0 a > 0 se publica `StockRepuesto` (dispara alertas de stock por correo).

## Cupos de eventos

1. `cupos_ocupados <= cupos_totales` (CHECK). Cupos disponibles = `cupos_totales − cupos_ocupados`.
2. Ocupar: `UPDATE evento SET cupos_ocupados = cupos_ocupados + :q WHERE id = :id AND publicado
   AND eliminado_en IS NULL AND fecha_hora > now() AND cupos_ocupados + :q <= cupos_totales`.
   0 filas → `event-past` si la fecha pasó, si no `event-full`.
3. El contador se mueve en la misma transacción que la inscripción (`RESERVADA`, `CONFIRMADA`, `CANCELADA`, `EXPIRADA`).
4. Cancelación por el cliente: solo su propia inscripción vigente y antes de la fecha del evento; libera los cupos.
   Política de reembolso: **pregunta abierta** (no implementar reembolso automático).

## Agenda y cumpleaños

1. Tipos de evento: `league-challenge`, `league-cup`, `torneo-tcg`, `prelanzamiento`, `lanzamiento` (ediciones nuevas
   de cartas y juegos de mesa), `videojuegos`, `cumpleanos`, `otros`.
2. Cumpleaños = bloque horario `[inicio, fin)` en que **la tienda cierra al público**. No vende cupos (`cupos_totales`
   NULL, precio 0). Se publica en la agenda como `tiendaCerrada`, **sin datos del cliente**.
3. Un cumpleaños no se traslapa con ningún evento vigente; un evento normal no se traslapa con un cumpleaños; los
   eventos normales sí pueden convivir. Eventos sin hora de término ocupan `pp.eventos.duracion-estimada` (3 h).
   Cambios de agenda serializados con advisory lock + `EXCLUDE` entre cumpleaños en la base (V7).
4. Solicitud: el CLIENTE pide un bloque (anticipación mínima 48 h, duración 1–6 h, configurable) → `PENDIENTE`.
   La tienda (VENDEDOR/ADMIN) aprueba (crea el evento y reserva el bloque) o rechaza con motivo. El cliente solo
   cancela mientras está pendiente. El pago del cumpleaños se coordina en la tienda (POS), no en la web.

## Límite por cliente (productos y eventos)

1. `limite_por_cliente` `NULL` = ilimitado; si no, entero > 0.
2. Se valida: ya comprado en pedidos válidos (`PENDIENTE_PAGO`, `PAGADO`, `LISTO_RETIRO`, `ENTREGADO`) +
   inscripciones vigentes + lo que intenta comprar ≤ límite. Si no → `purchase-limit` con `maxAllowed` restante.
3. Serializar por cliente: `SELECT id FROM usuario WHERE id = :cliente FOR UPDATE` al inicio del checkout.
4. Ventas de mostrador al cliente anónimo `mostrador@primerplayer.cl` **no** acumulan límite.
5. Cualquier rol interno lo define al crear; **solo ADMIN** lo cambia después (si no → 403 `limit-admin-only`).

## Validación de líneas (carrito web y POS)

Códigos que el frontend ya mapea:

| `code` | Cuándo |
| --- | --- |
| `empty` | Sin líneas. |
| `invalid-quantity` | Cantidad no entera o < 1 (máx. 999). |
| `item-unavailable` | No existe, está eliminado o no publicado. |
| `out-of-stock` | Stock 0. |
| `stock-limit` | Cantidad > stock. |
| `event-full` | Cupos insuficientes. |
| `event-past` | El evento ya ocurrió. |
| `purchase-limit` | Supera el límite acumulado (solo web). |

## Visibilidad y borrado

- Productos y eventos no publicados no aparecen en la API pública; sí en `/admin/**`.
- Eliminar = borrado lógico (`eliminado_en`). El historial de pedidos nunca se rompe.

## Producto

- Hasta 5 imágenes (`posicion` 0–4), exactamente una portada; cada imagen es archivo subido **o** URL `https://` externa.
  El servidor **no descarga** URLs externas (evita SSRF).
- Código de barras: opcional y único. Cualquier rol interno lo define al crear; **después solo ADMIN** puede
  agregarlo (si no tenía) o cambiarlo; para VENDEDOR es inmutable (`barcode-immutable`, 409). Formato EAN-8, UPC-A (12) o
  EAN-13 con dígito verificador válido. (Pregunta abierta: si la tienda usa códigos internos, relajar a 8–14 dígitos.)
- Descripción con formato simple (párrafos, `## `, `- `); se guarda como texto, nunca HTML.
- Categoría asignada siempre es una categoría **hoja**.

## Pedido

- Código `PP-<año>-<6 dígitos>` desde `pedido_codigo_seq` (global, no se reinicia), p. ej. `PP-2026-000123`.
- Canal `web` (cliente registrado) o `tienda` (POS, con `vendedor_id` obligatorio).
- Estados: `PENDIENTE_PAGO` → `PAGADO` → `LISTO_RETIRO` → `ENTREGADO`; además `RECHAZADO`, `EXPIRADO`, `ANULADO`, `REEMBOLSADO`.
- Ítems guardan `nombre` y `precio_unitario` del momento (snapshot); inscripciones guardan título, fecha, lugar y precio.
- `Idempotency-Key` obligatoria en checkout y venta POS: misma clave + mismo cuerpo → devuelve el pedido existente;
  otro cuerpo → 409 `idempotency-conflict` (se compara SHA-256 del cuerpo).

## Pagos de mostrador (pago mixto)

- Σ `monto` de todos los pagos = total (validado en servicio y con trigger diferido en BD) → si no `payments-mismatch`.
- `efectivo`: `efectivo_recibido` y `vuelto_entregado` obligatorios; `monto` = parte del total cubierta.
  Vuelto calculado = recibido − monto. Si el declarado difiere → advertencia `cash-change-mismatch`; solo se
  acepta con `confirmarVueltoDistinto: true`. Efectivo neto en caja = recibido − vuelto entregado.
- `debito`, `credito`, `prepago`: `numero_boleta` obligatorio (`receipt-required`). La boleta se emite en otro sistema.
- `transferencia`: comprobante obligatorio (`transfer-proof-required`), subido antes a `/admin/media/comprobantes`.
- `cartas-tcg`: nombre, código y valor de la carta (`tcg-card-required`). `saldo_a_favor` = valor − monto cubierto.
  (Pregunta abierta: cómo se entrega el saldo a favor.)
- `nota-credito`: solo el monto.
- `tarjeta-credito`: obsoleto; solo lectura de ventas antiguas, nunca aceptarlo en ventas nuevas.

## Checkout web con Webpay Plus

1. `POST /checkout`: en una transacción → bloquear cliente, validar, descontar stock y ocupar cupos, crear pedido
   `PENDIENTE_PAGO` con `expira_en = now() + 15 min` (configurable `pp.checkout.reserva-minutos`), inscripciones `RESERVADA`.
2. **Fuera** de la transacción: `transaction.create(buy_order = código, session_id, monto, return_url)`.
3. Retorno (`GET` o `POST /pagos/webpay/retorno`):
   - solo `token_ws` → bloquear la transacción (`FOR UPDATE`), si ya está resuelta no repetir; `commit`.
     Pagado si `response_code = 0`, `status = AUTHORIZED` y monto = total → `PAGADO`, inscripciones `CONFIRMADA`.
     Si no → `RECHAZADO` y reponer stock y cupos.
   - `TBK_TOKEN` sin `token_ws` → anulado por el usuario → `ANULADO`, reponer.
   - `token_ws` + `TBK_TOKEN` → error o timeout del formulario → `RECHAZADO`, reponer.
   - Siempre redirigir 303 a `/pago/resultado?pedido=<código>`.
4. Job cada 60 s (ShedLock) con `FOR UPDATE SKIP LOCKED` sobre pedidos vencidos: consulta `status` en Webpay;
   si está autorizado lo confirma, si no → `EXPIRADO` y repone.
5. Pedidos de total 0 (eventos gratuitos) se confirman sin Webpay.

## Caja

- Un cierre por día local (`America/Santiago`), PK = fecha.
- Resumen del día: pedidos válidos de `[00:00, 24:00)` hora de Chile; subtotales por método (en pago mixto cada
  método aporta solo su parte); efectivo neto = Σ(recibido − vuelto); productos vendidos.
- Esperado en caja = fondo inicial + efectivo neto. Diferencia = contado − esperado → `cuadrada` (0), `sobrante` (>0), `faltante` (<0).
- Todo se calcula en el servidor; el cliente solo envía fondo, contado y observaciones.
- Volver a guardar: copia la versión anterior a `cierre_caja_version` y sube `version`.
- Ventas web no cuentan en el efectivo esperado (no pasan por la caja física).

## Reportes (solo ADMIN)

- Día: total, variación vs. día anterior, órdenes, ticket promedio, monto productos vs. eventos.
- Por método de pago (web aparece como "Tienda web"), por hora, por día del mes, por mes del año.
- Top 5 por **unidades** (se muestra el monto al lado).
- `GROUP BY` con `AT TIME ZONE 'America/Santiago'`; sin vistas materializadas por ahora.

## Auditoría

Registrar quién, cuándo, IP, entidad, antes y después para: precio, stock, límites, publicación, eliminación,
ventas POS, anulaciones, reembolsos, cierres de caja y cambios de rol. Vía eventos de dominio (asíncrono).
