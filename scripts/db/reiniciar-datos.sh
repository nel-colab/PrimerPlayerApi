#!/usr/bin/env bash
# Reinicia los datos de la base local.
# Uso (Git Bash, desde la raíz del proyecto):
#   ./scripts/db/reiniciar-datos.sh                 vacía todo y carga los datos de DEMOSTRACIÓN
#   ./scripts/db/reiniciar-datos.sh --entrega       vacía todo y deja solo lo necesario para operar
#                                                   (admin + categorías, franquicias, configuración y mostrador)
#   ./scripts/db/reiniciar-datos.sh --solo-limpiar  vacía todo (solo queda el admin; la app no es usable así)
set -euo pipefail
cd "$(dirname "$0")/../.."

psql_local() {
  docker compose exec -T postgres psql -U pp -d primerplayer -v ON_ERROR_STOP=1 -q
}

echo "==> Vaciando todas las tablas (solo queda admin@primerplayer.cl)..."
psql_local < scripts/db/01-limpiar-base.sql > /dev/null

case "${1:-}" in
  --solo-limpiar)
    echo "==> Listo: base vacía. Ojo: sin categorías no se pueden crear productos (corre 03-datos-base.sql)."
    ;;
  --entrega)
    echo "==> Cargando solo los datos base (sin productos, eventos ni ventas)..."
    psql_local < scripts/db/03-datos-base.sql
    echo "==> Listo para entregar: tienda vacía, entra con admin@primerplayer.cl y carga el stock real."
    ;;
  "")
    echo "==> Poblando con datos de demostración..."
    psql_local < scripts/db/02-poblar-base.sql
    echo "==> Listo. Entra con cliente@primerplayer.cl / PrimerPlayer2026!"
    ;;
  *)
    echo "Opción desconocida: $1 (usa --entrega, --solo-limpiar o nada)" >&2
    exit 1
    ;;
esac
