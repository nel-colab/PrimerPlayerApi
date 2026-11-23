# Despliegue en producción — runagard.cl

Todo corre en un VPS (Contabo, Ubuntu 24.04, 8 GB) con Docker. El código está en GitHub y Jenkins despliega solo
con cada `git push` a `main`.

```
Internet ──► Caddy :443 (HTTPS automático, Let's Encrypt)
              ├─ runagard.cl            → front (Angular SSR)   ·  /api/** → api (Spring Boot)  ·  /salud → estado
              ├─ media.runagard.cl      → MinIO (solo lectura: imágenes y comprobantes firmados)
              ├─ jenkins.runagard.cl    → Jenkins    ┐
              ├─ sonar.runagard.cl      → SonarQube  ├─ clave de Caddy + login propio de cada herramienta
              └─ grafana.runagard.cl    → Grafana    ┘
PostgreSQL: red interna sin internet · Actuator/métricas: puerto 8081, solo dentro de Docker
```

| Archivo | Qué es |
|---|---|
| `docker-compose.yml` | Tienda: caddy, front, api, postgres, minio (proyecto `runagard`) |
| `docker-compose.ops.yml` | Herramientas: jenkins, sonarqube, prometheus, grafana, loki, alloy, exporters (`runagard-ops`) |
| `caddy/Caddyfile` | Dominios, HTTPS y rutas |
| `jenkins/` | Imagen de Jenkins (JDK 25, Node 24, Docker) y su configuración como código (`casc/casc.yaml`) |
| `prometheus/`, `grafana/`, `loki/`, `alloy/` | Monitoreo: métricas, paneles y logs |
| `.env.example` | Todas las variables. En el VPS se copia a `.env` (nunca va a git) |
| `scripts/` | Preparar el servidor y respaldar la base |

Los `Jenkinsfile` están en la raíz de cada repo (`PrimerPlayerApi` y `PrimerPlayerFront`).

---

## 1. DNS en DonWeb

Zona DNS de `runagard.cl` → registros **A** apuntando a la IP del VPS:

| Nombre | Tipo | Valor |
|---|---|---|
| `@` | A | IP del VPS |
| `www` | A | IP del VPS |
| `media` | A | IP del VPS |
| `jenkins` | A | IP del VPS |
| `sonar` | A | IP del VPS |
| `grafana` | A | IP del VPS |

Opcional, `runagard.store` y `runagard.online` → redirigen a `runagard.cl`: registros **A** `@` y `www` → IP del VPS,
y luego activar `caddy/sitios/redirecciones.caddy.ejemplo` (instrucciones dentro del archivo).

Borra registros A/CNAME antiguos de esos nombres (no toques los MX). Comprueba desde tu PC: `nslookup jenkins.runagard.cl`.

## 2. Servidor seguro

Si aún no lo hiciste: llave SSH, usuario `deploy` con sudo, sin login de root ni contraseñas por SSH, `ufw` con
OpenSSH/80/443, `fail2ban`, actualizaciones automáticas, Docker y swap. Jenkins escribe como uid 1000; si tu usuario
es otro (en Contabo, `ubuntu` ya tiene el 1000), el script del paso 3 los une por grupo.

## 3. Traer la configuración al VPS

Como `deploy`:

```bash
git clone https://github.com/nel-colab/PrimerPlayerApi.git ~/PrimerPlayerApi
# usuario de GitHub + el token como contraseña (paso 7); no queda guardado

sudo bash ~/PrimerPlayerApi/deploy/scripts/preparar-servidor.sh "$USER"   # sysctl de SonarQube y /opt/runagard
exit    # vuelve a entrar por SSH (toma el grupo nuevo)

cp -r ~/PrimerPlayerApi/deploy/. /opt/runagard/deploy/
cd /opt/runagard/deploy
cp .env.example .env && chmod 660 .env     # tú y Jenkins (grupo compartido); nadie más
```

Desde aquí, el pipeline de la API mantiene `/opt/runagard/deploy` sincronizado con el repo (sin tocar `.env`).

## 4. Completar `.env`

```bash
nano /opt/runagard/deploy/.env
```

Genera cada clave **en el servidor**:

```bash
openssl rand -base64 24 | tr -d '/+='   # POSTGRES_PASSWORD, MINIO_ROOT_PASSWORD, SONAR_DB_PASSWORD, contraseñas de admin
openssl rand -base64 48                 # PP_AUTH_JWT_SECRETO
openssl rand -hex 32                    # GITHUB_WEBHOOK_SECRET
getent group docker | cut -d: -f3       # DOCKER_GID
docker run --rm caddy:2 caddy hash-password --plaintext 'CLAVE_DEL_EQUIPO'   # OPS_PASSWORD_HASH (entre comillas simples)
```

Guarda las contraseñas en un gestor (Bitwarden, 1Password…). `SONAR_TOKEN` y `GITHUB_TOKEN` se completan después.
El correo (`SPRING_MAIL_*`) puede quedar para el final: sin él la tienda funciona, pero no salen correos.

## 5. Levantar la base de la tienda

```bash
cd /opt/runagard/deploy
docker compose up -d caddy postgres minio
docker compose logs -f caddy        # espera "certificate obtained successfully" para cada dominio (Ctrl+C)
```

`https://runagard.cl` responde **502** hasta que Jenkins despliegue el front y la API (paso 9): es normal.

## 6. Levantar las herramientas

```bash
docker compose -f docker-compose.ops.yml up -d --build     # la primera vez tarda ~5 min (imagen de Jenkins)
docker compose -f docker-compose.ops.yml ps
```

### SonarQube

1. Abre `https://sonar.runagard.cl` (primero pide la clave de Caddy: `OPS_USUARIO` + la clave del equipo).
2. Entra con `admin` / `admin` y **cámbiala**.
3. **My Account → Security → Generate Tokens**: tipo *Global Analysis Token*, sin vencimiento o 1 año.
   Copia el token a `SONAR_TOKEN` en `.env`.
4. **Administration → Configuration → Webhooks → Create**: nombre `Jenkins`,
   URL `http://jenkins:8080/sonarqube-webhook/` (así Jenkins sabe si pasó el quality gate).
5. Recarga Jenkins con el token nuevo: `docker compose -f docker-compose.ops.yml up -d jenkins`.

## 7. GitHub

1. **Token de solo lectura**: GitHub → Settings → Developer settings → *Fine-grained tokens* → Generate.
   Repositorios: `PrimerPlayerApi` y `PrimerPlayerFront`. Permisos: **Contents: Read-only** (Metadata se agrega solo).
   Copia a `GITHUB_TOKEN` (y tu usuario a `GITHUB_USER`) y recarga Jenkins como en el paso 6.5.
2. **Webhook en cada repo**: Settings → Webhooks → Add webhook
   - Payload URL: `https://jenkins.runagard.cl/github-webhook/`
   - Content type: `application/json`
   - Secret: el valor de `GITHUB_WEBHOOK_SECRET`
   - Eventos: *Just the push event*

## 8. Jenkins

`https://jenkins.runagard.cl` → clave de Caddy → usuario `JENKINS_ADMIN_USER` / `JENKINS_ADMIN_PASSWORD`.
Ya vienen creados los jobs **runagard-api** y **runagard-front** (rama `main`, `Jenkinsfile` del repo).

Cada pipeline: **pruebas → SonarQube → quality gate → imagen Docker → despliegue → verificación de salud**.
Si la nueva versión no queda sana en unos minutos, vuelve sola a la anterior.

## 9. Primer despliegue

En Jenkins: **runagard-api → Construir ahora** (≈10 min la primera vez: descarga dependencias y corre 287 pruebas).
Cuando termine, **runagard-front → Construir ahora**. Luego:

- `https://runagard.cl` → la tienda
- `https://runagard.cl/salud` → `{"status":"UP"}`

Desde aquí, cada `git push` a `main` despliega solo.

## 10. Tu usuario administrador

Regístrate en `https://runagard.cl` con tu correo y luego, en el VPS:

```bash
cd /opt/runagard/deploy
docker compose exec postgres psql -U runagard -d runagard -c \
  "UPDATE usuario SET rol = 'ADMIN', email_verificado_en = coalesce(email_verificado_en, now()) WHERE email = 'TU_CORREO';"
```

Vuelve a iniciar sesión y completa teléfono y RUT en tu perfil (el panel lo exige). Los demás roles se asignan
desde **Usuarios** en el panel.

## 11. Grafana

`https://grafana.runagard.cl` → clave de Caddy → `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`.
Prometheus y Loki ya están conectados. Importa paneles: **Dashboards → New → Import → ID → Load**:

| ID | Panel |
|---|---|
| `1860` | Node Exporter Full (CPU, RAM, disco y red del VPS) |
| `19004` | Spring Boot 3.x Statistics (API: peticiones, errores, JVM) — elige el job `api` |
| `14282` | cAdvisor (consumo por contenedor) |
| `9628` | PostgreSQL Database |

Logs: **Explore → Loki** → `{servicio="api"}` (o `caddy`, `front`, `postgres`…).

Alertas recomendadas (Alerting → Alert rules, contacto por correo): disco > 85 %, RAM > 90 % por 10 min,
`up{job="api"} == 0` por 2 min, y respuestas 5xx de la API.

## 12. Respaldos

```bash
crontab -e      # como deploy
30 4 * * * /opt/runagard/deploy/scripts/respaldo-db.sh >> /opt/runagard/deploy/respaldos/respaldo.log 2>&1
```

Pruébalo una vez a mano. Además del Auto Backup de Contabo, copia los respaldos fuera del VPS (rclone a Backblaze B2
o Cloudflare R2; instrucciones en el script). **Prueba restaurar** al menos una vez.

## 13. Monitoreo externo

UptimeRobot (gratis): monitor HTTPS a `https://runagard.cl/salud` cada 5 min, con aviso a tu correo.

---

## Día a día

| Quiero… | Cómo |
|---|---|
| Desplegar | `git push` a `main` (o "Construir ahora" en Jenkins) |
| Volver a la versión anterior | `cd /opt/runagard/deploy && API_TAG=anterior docker compose up -d api` (o `FRONT_TAG=anterior … front`) |
| Volver a un commit concreto | `API_TAG=<8 primeros caracteres del commit> docker compose up -d api` |
| Cambiar una variable | editar `.env` y `docker compose up -d api` (o el servicio que la use) |
| Ver logs | Grafana → Explore → Loki, o `docker compose logs -f api` |
| Cambiar Caddy | push del repo de la API (el pipeline recarga Caddy) |
| Cambiar herramientas | push y luego `docker compose -f docker-compose.ops.yml up -d` en el VPS |
| Actualizar Jenkins y plugins | `docker compose -f docker-compose.ops.yml build --pull jenkins && docker compose -f docker-compose.ops.yml up -d jenkins` |

## Memoria (8 GB)

Topes por servicio: API 1,25 GB · PostgreSQL 768 MB · front 384 MB · MinIO 512 MB · Jenkins 3 GB (incluye los
builds) · SonarQube 2,5 GB · monitoreo ~1,3 GB. No se usan todos a la vez; Jenkins corre un build a la vez.
Si Grafana muestra RAM sostenida > 90 %, lo primero es mover Jenkins y SonarQube a otro VPS: solo cambia dónde
corre `docker-compose.ops.yml`.

## Problemas comunes

- **Caddy no obtiene el certificado**: el DNS aún no apunta al VPS, o el puerto 80 está cerrado (`sudo ufw status`).
- **SonarQube se reinicia en bucle**: falta `vm.max_map_count` → `sudo bash scripts/preparar-servidor.sh`.
- **Jenkins no puede usar Docker**: `DOCKER_GID` incorrecto en `.env`.
- **El build de la API falla en las pruebas con "Could not find a valid Docker environment"**: revisa que Jenkins
  monte `/var/run/docker.sock` y que `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` esté en su entorno.
- **Los correos llegan a spam**: falta SPF/DKIM/DMARC del proveedor SMTP en el DNS de DonWeb.
