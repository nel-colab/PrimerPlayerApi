# Modelo de datos

El DDL real vive en `src/main/resources/db/migration/` (V1–V6). **Esa es la fuente de verdad**: léela antes de
escribir entidades JPA o consultas. Este archivo es el mapa.

## Tablas por módulo (cada módulo solo escribe en las suyas)

| Módulo | Tablas | Migración |
| --- | --- | --- |
| `identidad` | `usuario`, `refresh_token`, `token_un_uso` | V1 |
| `clientes` | `direccion`, `favorito`, `alerta_stock` | V1, V2 |
| `catalogo` | `categoria`, `franquicia`, `producto` (excepto la columna `stock`), `producto_imagen` | V2 |
| `inventario` | `producto.stock` (única columna que escribe), `movimiento_stock`, `config_inventario` | V2 |
| `media` | `archivo` | V2 |
| `eventos` | `evento`, `inscripcion`, `solicitud_cumpleanos` | V3, V7 |
| `ventas` | `pedido`, `pedido_item`, secuencia `pedido_codigo_seq` | V3 |
| `pagos` | `pago`, `transaccion_webpay` | V4 |
| `caja` | `cierre_caja`, `cierre_caja_version` | V5 |
| `auditoria` | `auditoria` | V5 |
| `notificaciones` | `correo_enviado` | V5 |
| infraestructura | `shedlock`, `event_publication` (Spring Modulith) | V5 / Modulith |

`checkout`, `pos` y `reportes` no tienen tablas propias: usan los servicios `api` de otros módulos
(`reportes` solo lee).

## Convenciones del esquema

- Claves `BIGINT GENERATED ALWAYS AS IDENTITY` (`SMALLINT` en catálogos chicos, `UUID` en tokens y archivos).
- Montos `BIGINT` (CLP). Fechas `TIMESTAMPTZ`. Correos `CITEXT`.
- Nombres de tablas y columnas en español, `snake_case`, singular.
- Borrado lógico con `eliminado_en`; los índices públicos son parciales `WHERE publicado AND eliminado_en IS NULL`.
- `version INTEGER` en `producto` y `evento` para `@Version` (bloqueo optimista del mantenedor).
- Restricciones de negocio en la base: `CHECK (stock >= 0)`, `CHECK (cupos_ocupados <= cupos_totales)`,
  `UNIQUE` parcial de `codigo_barras`, `CHECK` por método de pago, trigger diferido `tg_pagos_cuadran`
  (Σ pagos = total al hacer COMMIT), `ck_pedido_vendedor` (canal `tienda` ⇔ vendedor).

## Mapeo frontend ↔ base

| Frontend (`Product`) | Base |
| --- | --- |
| `categoria` (slug) | `producto.categoria_id` → `categoria.slug` |
| `imagen` / `imagenes[]` | `producto_imagen` (portada = `es_portada`), URL desde `archivo.clave` o `url_externa` |
| `franquicia` (texto) | `producto.franquicia_id` → `franquicia.nombre` |
| `limitePorUsuario` | `limite_por_cliente` |
| `fecha` | `fecha_ingreso` |

| Frontend (`Evento`) | Base |
| --- | --- |
| `fecha` | `fecha_hora` (`timestamptz`) |
| `cuposTotales` | `cupos_totales`; disponibles = `cupos_totales − cupos_ocupados` |

| Frontend (`PurchaseOrder`) | Base |
| --- | --- |
| `code` | `pedido.codigo` |
| `email` | `usuario.email` del `cliente_id` |
| `seller` | `usuario.email` del `vendedor_id` |
| `items[].unitPrice` / `name` | `pedido_item.precio_unitario` / `nombre` (snapshot) |
| `payments[].details.cashReceived` / `changeGiven` | `pago.efectivo_recibido` / `vuelto_entregado` |
| `payments[].details.tcgCard` | `pago.carta_nombre`, `carta_codigo`, `carta_valor` |
| `payments[].details.receiptNumber` | `pago.numero_boleta` |
| `payments[].details.transferProof` | `pago.comprobante_archivo_id` (URL firmada al leer) |

Roles: frontend `usuario` ↔ base `CLIENTE`; `vendedor` ↔ `VENDEDOR`; `admin` ↔ `ADMIN`.

## Consultas típicas

```sql
-- Límite acumulado de un cliente para un producto
SELECT COALESCE(SUM(i.cantidad), 0)
FROM pedido_item i JOIN pedido p ON p.id = i.pedido_id
WHERE p.cliente_id = :cliente AND i.producto_id = :producto
  AND p.estado IN ('PENDIENTE_PAGO','PAGADO','LISTO_RETIRO','ENTREGADO');

-- Rango de un día local de Chile, para caja y reportes
WHERE p.creado_en >= (:fecha::date)::timestamp AT TIME ZONE 'America/Santiago'
  AND p.creado_en <  (:fecha::date + 1)::timestamp AT TIME ZONE 'America/Santiago'

-- Código de pedido
SELECT 'PP-' || to_char(now() AT TIME ZONE 'America/Santiago', 'YYYY') || '-'
       || lpad(nextval('pedido_codigo_seq')::text, 6, '0');
```
