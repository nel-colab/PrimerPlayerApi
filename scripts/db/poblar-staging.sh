#!/usr/bin/env bash
# Vacía la base de STAGING y la llena con datos de demostración (01-limpiar-base.sql + 02-poblar-base.sql).
# Se corre desde tu PC (Git Bash, raíz del proyecto); los SQL viajan por SSH al contenedor postgres-staging del VPS.
#
#   ./scripts/db/poblar-staging.sh deploy@217.216.64.25               volumen normal (escala 1)
#   ./scripts/db/poblar-staging.sh deploy@217.216.64.25 --escala 10   ~10× ventas, +900 productos, +90 clientes
#
# Pide la contraseña para TODAS las cuentas demo (staging es público: no se usa la del repo). También se puede pasar
# en la variable PP_STAGING_CLAVE. Nunca toca producción: los SQL se niegan a correr fuera de "runagard_staging".
set -euo pipefail
cd "$(dirname "$0")/../.."

DESTINO="${1:-}"
ESCALA=1
if [[ -z "$DESTINO" || "$DESTINO" == --* ]]; then
  echo "Uso: $0 usuario@servidor [--escala N]" >&2
  exit 1
fi
shift
while [[ $# -gt 0 ]]; do
  case "$1" in
    --escala) ESCALA="${2:-}"; shift 2 ;;
    *) echo "Opción desconocida: $1" >&2; exit 1 ;;
  esac
done
if ! [[ "$ESCALA" =~ ^[0-9]+$ ]] || (( ESCALA < 1 || ESCALA > 50 )); then
  echo "--escala debe ser un entero entre 1 y 50" >&2
  exit 1
fi

CLAVE="${PP_STAGING_CLAVE:-}"
if [[ -z "$CLAVE" ]]; then
  read -r -s -p "Contraseña para todas las cuentas demo de staging (mín. 12 caracteres): " CLAVE; echo
  read -r -s -p "Repítela: " CLAVE2; echo
  [[ "$CLAVE" == "$CLAVE2" ]] || { echo "No coinciden." >&2; exit 1; }
fi
(( ${#CLAVE} >= 12 )) || { echo "La contraseña debe tener al menos 12 caracteres." >&2; exit 1; }

echo "Esto BORRA todos los datos de staging (usuarios incluidos) en $DESTINO y carga datos demo con escala $ESCALA."
read -r -p "Escribe 'staging' para continuar: " OK
[[ "$OK" == "staging" ]] || { echo "Cancelado."; exit 1; }

# psql: dentro de comillas simples, \ y ' se escapan con \
CLAVE_PSQL=$(printf '%s' "$CLAVE" | sed -e 's/\\/\\\\/g' -e "s/'/\\\\'/g")

COMPOSE='cd /opt/runagard/deploy && docker compose -f docker-compose.staging.yml --env-file .env.staging'

echo "==> Vaciando y poblando staging (escala $ESCALA; puede tardar varios minutos)..."
{
  printf '\\set escala %s\n' "$ESCALA"
  printf "\\\\set clave '%s'\n" "$CLAVE_PSQL"
  cat scripts/db/01-limpiar-base.sql
  cat scripts/db/02-poblar-base.sql
} | ssh "$DESTINO" "$COMPOSE exec -T postgres-staging sh -c 'psql -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -v ON_ERROR_STOP=1 -q'"

echo "==> Reiniciando api-staging (sesiones y cachés quedan limpias)..."
ssh "$DESTINO" "$COMPOSE restart api-staging" > /dev/null

cat <<EOF
==> Listo. En https://staging.runagard.cl (con la contraseña que elegiste):
    admin@primerplayer.cl (ADMIN) · vendedor@primerplayer.cl (VENDEDOR) · cliente@primerplayer.cl (CLIENTE)
    vendedora@demo.primerplayer.cl (VENDEDOR) · clientes *@demo.primerplayer.cl$( (( ESCALA > 1 )) && printf ' · carga-0001..%04d@demo.primerplayer.cl' $(( (ESCALA - 1) * 10 )) )
EOF
