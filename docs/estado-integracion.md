# Estado de la integración (backend ↔ frontend)

Se actualiza al terminar cada tarea. **Última actualización:** 2026-11-17, después de conectar el POS al backend real (Block E).

| Pantalla / función | Backend | Frontend | ¿Muestra los datos del script 02? |
|---|---|---|---|
| Login, registro, sesión | ✅ | ✅ API | ✅ |
| Mantenedor de productos y stock (`/admin/stock`) | ✅ | ✅ API | ✅ |
| Movimientos y ajustes de stock | ✅ | ✅ API | ✅ |
| Categorías y franquicias | ✅ | ✅ API | ✅ |
| Eventos (público y admin) y agenda | ✅ | ✅ API | ✅ |
| Solicitudes de cumpleaños (cliente y tienda) | ✅ | ✅ API | ✅ |
| Catálogo público de productos (tienda, detalle, búsqueda, destacados, relacionados) | ✅ | 🔄 prompt entregado (Tarea A) | ⏳ cuando el front lo conecte |
| Página de códigos de barras (`/admin/codigo-barras`) — es la pantalla del POS | ✅ (lectura + venta) | ✅ API | ✅ |
| Mis pedidos (perfil del cliente) | ✅ | 🔄 prompt entregado (Tarea B) | ⏳ cuando el front lo conecte |
| Panel de pedidos y retiro en tienda (`/admin/pedidos`) | ✅ | 🔄 prompt entregado (Tarea C, página nueva) | ⏳ cuando el front lo conecte |
| Validación del carrito (precios, stock, cupos, límite por cliente) | ✅ | 🔄 prompt entregado | ⏳ cuando el front lo conecte |
| Checkout (pedido con reserva de stock y cupos por 15 min) | ✅ | 🔄 prompt entregado | ⏳ cuando el front lo conecte |
| Pago con Webpay Plus (ambiente de integración) | ✅ probado contra Transbank real | 🔄 prompt entregado (incluye `/pago/resultado`) | ⏳ cuando el front lo conecte |
| Vencimiento automático de reservas sin pago | ✅ cada minuto (ShedLock) | — (automático) | ✅ |
| Compra de cupos de eventos | ✅ vía checkout | 🔄 prompt entregado | ⏳ cuando el front lo conecte |
| Mis inscripciones (con cancelación que libera cupos) | ✅ | 🔄 prompt entregado | ⏳ cuando el front lo conecte |
| Inscritos de un evento (tienda) | ✅ | 🔄 prompt entregado (pantalla nueva) | ⏳ cuando el front lo conecte |
| Datos de la cuenta en el perfil (teléfono, RUT, miembro desde) y edición del perfil | ✅ (`GET /me`, `GET`/`PUT /me/perfil`) | ⚠️ muestra «—» | ⏳ cuando el front lo conecte |
| Verificación de correo, recuperación y cambio de contraseña | ✅ (`/auth/verificacion`, `/auth/recuperacion`, `/auth/restablecimiento`, `PUT /me/password`, `POST /me/verificacion`) | ⚠️ faltan las páginas `/cuenta/verificar` y `/cuenta/restablecer` | ⏳ cuando el front lo conecte |
| Correos (verificación, recuperación, pedido confirmado, alerta de stock) | ✅ módulo `notificaciones` (Mailpit en local) | — (automático) | ✅ |
| Direcciones | ✅ (`/me/direcciones`; solo se guardan: hay solo retiro) | ⚠️ lista vacía | ⏳ cuando el front lo conecte |
| Pagos dentro del pedido (`payments`: efectivo con vuelto, boleta, carta TCG, Webpay con autorización y últimos 4 dígitos) | ✅ | ⚠️ (mis pedidos y panel ya llegan con `payments`) | ⏳ cuando el front lo conecte |
| Lectura por código de barras (pistola = teclado + Enter; GET común) | ✅ | ✅ API | ✅ |
| POS: venta en mostrador con pago mixto | ✅ | ✅ API | ✅ |
| POS: anulación de venta (solo ADMIN) | ✅ | ⚠️ local (no hay botón aún) | ⏳ cuando el front lo conecte |
| Cierre de caja (resumen del día con pagos reales, cierre con historial de versiones, listado) | ✅ | ⚠️ local | ⏳ cuando el front lo conecte |
| Reportes y estadísticas (día con variación, por hora, por día del mes, por mes, métodos de pago, top 5) | ✅ solo ADMIN | ⚠️ local | ⏳ cuando el front lo conecte |
| Consulta de auditoría | ✅ solo ADMIN **(ya registra las acciones sobre usuarios)** | — (no hay pantalla) | ⏳ |
| **Administración de usuarios (búsqueda, indicadores, rol, baneo, lista negra, restablecer contraseña, reenviar verificación, cerrar sesiones; todo auditado)** | **✅ solo ADMIN** | **✅ API** | **✅** |
| Favoritos y alertas de stock (con aviso por correo al reponerse) | ✅ (`/me/favoritos`, `/me/alertas-stock`) | ⚠️ listas vacías | ⏳ cuando el front lo conecte |
| Subida de imágenes de productos (MinIO) y comprobantes de transferencia | ✅ (productos aceptan `archivoId`; eventos aún solo URL) | ⚠️ solo URL | ⏳ cuando el front lo conecte |
| **Correos y auditoría** | **✅ automáticos (Mailpit en local; auditoría en `GET /admin/auditoria`)** | — | Solo se ven en pgAdmin / Mailpit |
| Despacho a domicilio | ⏸️ pospuesto (decisión de negocio) | — | — |
| Reembolsos | ⏸️ pospuesto (decisión de negocio) | — | — |

**Leyenda:** ✅ listo · 🔄 prompt entregado, falta que el front lo aplique · ⚠️ funciona con datos locales del
navegador · ❌ no existe · ⏸️ pospuesto por decisión de negocio.

## Resumen

- **Backend:** **14 de 14 módulos implementados** (identidad, catálogo, inventario, media, eventos, ventas,
  checkout, pagos, pos, caja, reportes, clientes, notificaciones y auditoría): **81 endpoints**, 245 pruebas en verde.
- **Pendiente en el frontend:**
  - aplicar [`prompt-frontend-catalogo-pedidos.md`](prompt-frontend-catalogo-pedidos.md);
  - aplicar [`prompt-frontend-checkout-inscripciones.md`](prompt-frontend-checkout-inscripciones.md) (carrito,
    checkout, Webpay, `/pago/resultado`, cupos, mis inscripciones e inscritos);
  - falta escribir el prompt de lo nuevo: POS y pagos del pedido, caja, subida de archivos, reportes, cuenta del
    cliente (páginas `/cuenta/verificar` y `/cuenta/restablecer`) y administración de usuarios.
- **Pendiente con la tienda:** iniciar el contrato y la validación de Webpay Plus con Transbank para producción;
  definir la política de reembolso (hoy una inscripción cancelada libera el cupo pero no devuelve el dinero).
- **Limitaciones conocidas:** las imágenes de **eventos** aún se guardan solo como URL (los productos ya aceptan
  archivos subidos); al banear, el access token vigente sigue sirviendo hasta que expira (≤ 15 min), pero ya no
  puede renovarse.

## Historial

| Fecha | Cambio |
|---|---|
| 2026-11-14 | Prompt de diagnóstico para el front (`prompt-frontend-revision-integracion.md`): contrato completo de los 81 endpoints; el front responde con una tabla de qué falta por conectar |
| 2026-11-13 | **Administración de usuarios (`/admin/usuarios`, 10 endpoints, solo ADMIN): búsqueda e indicadores, cambio de rol, baneo (rechaza login y refresh con `account-banned` y cierra sesiones), lista negra (`blacklisted` en checkout y cumpleaños), restablecer contraseña, reenviar verificación, cerrar sesiones; protege al último admin y cada acción queda en auditoría (V9). Backend completo: 14 módulos** |
| 2026-11-13 | Cuenta del cliente: verificación de correo, recuperación y cambio de contraseña, perfil con teléfono/RUT, direcciones, favoritos y alertas de stock · módulo `notificaciones` (correos) · outbox de eventos (V8) |
| 2026-11-13 | Módulo `reportes`: `/admin/reportes/dia`, `/por-hora`, `/por-dia`, `/por-mes` · módulo `auditoria`: `GET /admin/auditoria` |
| 2026-11-13 | Módulo `media`: `POST /admin/media/imagenes`, `POST /admin/media/comprobantes`, `GET /admin/media/comprobantes/{id}`; productos con imágenes subidas; el POS ya cobra con transferencia |
| 2026-11-13 | Módulo `caja`: `GET /admin/caja/{fecha}`, `PUT /admin/caja/{fecha}/cierre`, `GET /admin/caja/cierres` |
| 2026-11-13 | Prompt del front para carrito, checkout, Webpay, `/pago/resultado`, cupos, mis inscripciones e inscritos (`prompt-frontend-checkout-inscripciones.md`); el POS y los pagos del pedido quedan para el prompt siguiente |
| 2026-11-13 | Pagos dentro del pedido (`payments` en mis pedidos, panel y POS) · módulo `pos`: lectura por código de barras (`GET /admin/pos/productos/codigo/{codigo}`), validación, venta con pago mixto y anulación |
| 2026-11-13 | Mis inscripciones (`GET /me/inscripciones`), cancelación por el cliente que libera cupos (`POST /me/inscripciones/{id}/cancelacion`) e inscritos de un evento (`GET /admin/eventos/{id}/inscritos`) |
| 2026-11-13 | Carrito, checkout y Webpay Plus: validación del carrito, checkout con reserva atómica e idempotencia, retorno de Webpay, vencimiento de reservas con ShedLock. Probado contra el ambiente de integración real de Transbank |
| 2026-11-13 | Catálogo público (4 endpoints) · módulo `ventas`: mis pedidos y panel con retiro en tienda (4 endpoints) · SDK de Webpay agregado · prompt del front para catálogo y pedidos |
| 2026-11-12 | Primera tabla: identidad, catálogo admin, inventario y eventos conectados |
