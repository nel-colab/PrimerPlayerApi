#!/usr/bin/env bash
# Respaldo diario de la base de la tienda: pg_dump comprimido en /opt/runagard/deploy/respaldos, se guardan 14 días.
# Programarlo con cron (crontab -e del usuario deploy):
#   30 4 * * * /opt/runagard/deploy/scripts/respaldo-db.sh >> /opt/runagard/deploy/respaldos/respaldo.log 2>&1
#
# Restaurar (¡reemplaza la base!):
#   gunzip -c respaldos/runagard-AAAAMMDD-HHMM.sql.gz | docker compose exec -T postgres psql -U runagard -d runagard
#
# Copia fuera del VPS: instalar rclone, configurar un remoto (p. ej. Backblaze B2 o Cloudflare R2) llamado "respaldo"
# y descomentar la línea de rclone al final.
set -euo pipefail

cd /opt/runagard/deploy
valor() { grep -E "^$1=" .env | head -1 | cut -d= -f2- | sed -e 's/[[:space:]]*#.*$//' -e "s/^['\"]//" -e "s/['\"]$//"; }
POSTGRES_USER=$(valor POSTGRES_USER)
POSTGRES_DB=$(valor POSTGRES_DB)
mkdir -p respaldos

archivo="runagard-$(date -u +%Y%m%d-%H%M).sql.gz"
docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --clean --if-exists \
  | gzip -9 > "respaldos/$archivo"

# Un respaldo vacío es peor que ninguno: falla fuerte para que se note.
if [ "$(stat -c %s "respaldos/$archivo")" -lt 1024 ]; then
  echo "$(date -u +%FT%TZ) ERROR: respaldo demasiado pequeño ($archivo)"
  exit 1
fi

find respaldos -name 'runagard-*.sql.gz' -mtime +14 -delete
echo "$(date -u +%FT%TZ) OK $archivo ($(du -h "respaldos/$archivo" | cut -f1))"

# rclone copy "respaldos/$archivo" respaldo:runagard-respaldos/
