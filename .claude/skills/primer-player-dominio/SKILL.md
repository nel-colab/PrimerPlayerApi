---
name: primer-player-dominio
description: Reglas de negocio, modelo de datos y contrato de API de Primer Player (stock, cupos de eventos, límite por cliente, pedidos, pagos de mostrador, Webpay, cierre de caja, roles). Úsala antes de implementar o revisar cualquier lógica de catálogo, inventario, eventos, checkout, POS, pagos, caja o reportes.
---

# Dominio de Primer Player

Tienda física en San Bernardo, Chile, con tienda online (retiro en tienda) y venta en mostrador (POS).
CLP enteros, locale `es-CL`, zona `America/Santiago`. Una sucursal, una caja por día, volumen bajo a medio.

## Cómo usar esta skill

1. Lee la referencia que corresponda a la tarea antes de escribir código:
   - Reglas que el servidor debe hacer cumplir → [referencias/reglas-negocio.md](referencias/reglas-negocio.md)
   - Tablas, columnas y restricciones → [referencias/modelo-datos.md](referencias/modelo-datos.md)
   - Endpoints, roles, códigos de error y JSON → [referencias/api.md](referencias/api.md)
2. Si la referencia no alcanza, busca la sección en `docs/arquitectura.md` (DDL completo en la sección 6,
   flujos en la 8, concurrencia en la 9, seguridad en la 10).
3. Si una regla es ambigua o no aparece, **no la inventes**: déjala como `// TODO(pregunta-abierta): ...`
   y menciónala en tu respuesta.

## Lo esencial (si solo lees esto)

- Stock nunca negativo; cupos vendidos nunca superan `cupos_totales`; no se venden cupos de eventos pasados.
- Límite por cliente acumulado entre pedidos válidos (`null` = ilimitado). Las ventas de mostrador al cliente
  anónimo `mostrador@primerplayer.cl` no acumulan límite.
- Solo ADMIN cambia un límite por cliente ya configurado. Cualquier rol interno lo define al crear.
- Código de barras único; tras crear el producto solo ADMIN lo agrega o cambia (VENDEDOR → `barcode-immutable`).
- Cada ítem de pedido guarda nombre y precio unitario del momento (snapshot).
- Borrado lógico (`eliminado_en`) para productos y eventos.
- Pago mixto en mostrador: Σ montos = total; reglas por método en reglas-negocio.md.
- Checkout web: pedido `PENDIENTE_PAGO` con reserva de 15 min → Webpay → `PAGADO` o se libera.
- Cierre de caja: uno por día local; volver a guardarlo crea una versión nueva en el historial.
