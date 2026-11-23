# Prompt: inicio de sesión con Google y verificación de correo en el frontend

> Copia el contenido de este documento y entrégalo al agente que trabaja en `PrimerPlayerFront`.
> Backend de referencia: `PrimerPlayerApi` (Spring Boot 4). Frontend: Angular 22.

---

## Contexto y objetivo

Trabaja en el repositorio **PrimerPlayerFront** (`E:\proyectos\PrimerPlayerFront`). La API está en
`E:\proyectos\PrimerPlayerApi` y ya implementa todo lo descrito aquí; el contrato vigente está en
`GET /api/v1/openapi.json`.

Objetivo:

1. Iniciar sesión y registrarse con Google desde el login y el registro.
2. Verificar el correo con Google desde "Mi perfil" (cliente) y desde el perfil del panel (vendedor/admin).
3. Llevar al personal (VENDEDOR/ADMIN) a completar su perfil cuando el panel responda que le falta algo.

No hagas cambios en el backend. Si el contrato de abajo no coincide con el OpenAPI, informa el endpoint y la
diferencia antes de inventar uno. No cambies hosting, dominios ni configuración de producción.

### Ya existe en el frontend (sin commit): revísalo y complétalo, no lo rehagas

- `src/app/core/services/google-identity.ts`: carga `GET /auth/google/config` y el script de Google Identity
  Services (`https://accounts.google.com/gsi/client`), hace `initialize` en modo popup y `renderButton`.
- `src/app/shared/google-button/google-button.ts`: `<app-google-button texto separador ocupado (credential)>`.
  Siempre se ve: el botón oficial si `enabled = true`; si no, un botón con el mismo aspecto que avisa que Google
  no está disponible.
- `Auth.loginConGoogle(credential)` en `core/services/auth.ts`; `logout()` llama a `google.olvidarSeleccion()`.
- Botón en login y registro; botón de verificación en `features/public/cuenta/perfil` y `features/admin/perfil`.
- `MeApi.verificarConGoogle(credential)` en `me-api.ts`; `PerfilUsuario.profileComplete`.
- Códigos de error nuevos y `MENSAJES_GOOGLE` en `core/http/api-problem.ts`.
- RUT con formato al escribir (`shared/rut-input`) y validador módulo 11 (`core/utils/rut.ts`).
- Se eliminó la página `oauth-callback` y su ruta: **no la vuelvas a crear**. El access token nunca va en la URL.

## Contrato de la API

Base: `/api/v1`. Errores en `application/problem+json` con campo `code`.

### `GET /auth/google/config` (público)

```json
{ "clientId": "1234-abc.apps.googleusercontent.com", "enabled": true }
```

`enabled = false` y `clientId = ""` cuando el backend no tiene `PP_AUTH_GOOGLE_CLIENT_ID`. El frontend **no**
guarda el client id en `environment.ts`: siempre lo pide a este endpoint.

### `POST /auth/google` (público)

Petición: `{ "credential": "<ID token de Google>" }`, con `withCredentials: true`.

Respuesta 200: **la misma que `POST /auth/login`** (`{ accessToken, expiresIn, user }`) más la cookie HttpOnly
`pp_rt`. Reutiliza el mismo manejo de sesión que el login con contraseña.

Comportamiento:

- Correo sin cuenta → crea una cuenta de cliente con el correo ya verificado y **sin contraseña**. Si después quiere
  una, la crea con "¿Olvidaste tu contraseña?".
- Correo con cuenta existente → la vincula a Google y marca el correo como verificado.
- Las cuentas sin contraseña no pueden entrar con el formulario de correo y contraseña (responde el `unauthorized`
  normal).

| Código | HTTP | Mensaje sugerido |
|---|---|---|
| `invalid-google-token` | 401 | No pudimos validar tu cuenta de Google. Inténtalo de nuevo. |
| `google-account-in-use` | 409 | Ese correo ya está vinculado a otra cuenta de Google. |
| `account-banned` | 423 | Mensaje de baneo actual (ya existe). |
| `google-unavailable` | 503 | El acceso con Google no está disponible en este momento. |

### `POST /me/verificacion/google` (bearer; cliente, vendedor o admin)

Petición: `{ "credential": "<ID token de Google>" }`.

Respuesta 200: el perfil (`GET /me/perfil`) con `emailVerified = true`. Actualiza el estado del perfil en pantalla
y oculta el botón de verificación.

| Código | HTTP | Mensaje sugerido |
|---|---|---|
| `google-email-mismatch` | 409 | Elegiste una cuenta de Google con otro correo. Usa la del correo de tu cuenta. |
| `google-account-in-use` | 409 | Esa cuenta de Google ya está vinculada a otro usuario. |
| `invalid-google-token` | 401 | Igual que arriba. |
| `google-unavailable` | 503 | Igual que arriba. |

### Perfil (`GET /me/perfil` y `PUT /me/perfil`)

```json
{
  "email": "ana@correo.cl", "name": "Ana Pérez", "role": "vendedor",
  "phone": "+56 9 1234 5678", "rut": "12345678-5",
  "memberSince": "2026-10-01T09:00:00-03:00",
  "emailVerified": false, "mustChangePassword": false,
  "profileComplete": false
}
```

`profileComplete` = tiene nombre, teléfono y RUT.

## Reglas que el frontend debe reflejar

### Compras

Para comprar (checkout, inscripciones a eventos) hay que iniciar sesión con el **correo verificado**; si no,
la API responde `403 email-not-verified`. Cuando eso ocurra, ofrece las dos salidas:

- "Verificar con Google" (el botón de `<app-google-button texto="continue_with">` que llama a
  `MeApi.verificarConGoogle`).
- "Reenviar correo de verificación" (flujo existente).

Vendedor y admin también pueden comprar, ver "Mis pedidos" y guardar direcciones: no ocultes esas opciones por rol.

### Panel `/admin/**` (vendedor y admin)

Todo endpoint `/admin/**` responde 403 si el usuario del personal:

| Código | Causa | Qué hacer |
|---|---|---|
| `email-not-verified` | correo sin verificar | redirigir al perfil del panel y mostrar el botón "Verificar con Google" |
| `profile-incomplete` | falta nombre, teléfono o RUT (`detail` dice cuáles) | redirigir al perfil del panel con el `detail` visible |

Tareas:

1. En el interceptor HTTP (o un guard del panel), al recibir esos dos códigos desde una URL `/admin/`, navega a la
   ruta del perfil del panel **una sola vez** (sin bucles si el propio perfil falla) y muestra el mensaje.
2. Al entrar al panel, si `GET /me/perfil` trae `emailVerified = false` o `profileComplete = false`, muestra un
   aviso fijo que lleve al perfil, en vez de dejar que cada pantalla falle por separado.
3. En el perfil del panel, teléfono y RUT son obligatorios; el RUT usa `appRutInput` y `rutValidator`.
4. Tras guardar el perfil o verificar el correo, vuelve a cargar el perfil y quita el aviso.

## Detalles de Google Identity Services

- Modo **popup** (`ux_mode: 'popup'`) con `renderButton`; no uses redirección ni el flujo de código OAuth.
- No uses el *client secret*: el frontend nunca lo necesita.
- GIS tiene un solo callback global: `GoogleIdentity` ya redirige el token al último botón clickeado. Si agregas
  otro botón, usa el mismo servicio.
- En SSR no cargues el script (ya se controla con `isPlatformBrowser`).
- En local el origen `http://localhost:4200` debe estar en "Orígenes autorizados de JavaScript" del Client ID.
- No guardes el `credential` en `localStorage`, logs ni la URL.

## Pruebas

- `google-identity`: con `enabled = false` no carga el script; con `enabled = true` inicializa y dibuja el botón
  (simula `window.google.accounts.id`).
- `google-button`: muestra el botón alternativo cuando no hay Google y emite `credential` cuando lo hay.
- `Auth.loginConGoogle`: `POST /api/v1/auth/google` con `withCredentials` y guarda la sesión igual que `login`.
- `MeApi.verificarConGoogle`: `POST /api/v1/me/verificacion/google`.
- Interceptor/guard: `403 profile-incomplete` y `403 email-not-verified` en `/admin/**` llevan al perfil del panel
  sin bucle.
- Mapeo de los códigos nuevos a los mensajes de la tabla.

Al terminar: `ng build` sin errores y `ng test --watch=false` en verde. Resume qué archivos cambiaste y qué
quedó pendiente.
