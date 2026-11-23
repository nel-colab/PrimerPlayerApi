#!/usr/bin/env bash
# Ajustes del VPS que necesitan los contenedores. Ejecutar UNA vez, con sudo, después de instalar Docker:
#   sudo bash deploy/scripts/preparar-servidor.sh deploy
# (el argumento es tu usuario de trabajo; por defecto, quien ejecuta sudo)
set -euo pipefail

usuario="${1:-${SUDO_USER:-deploy}}"

# SonarQube (Elasticsearch interno) no arranca sin esto.
cat > /etc/sysctl.d/99-sonarqube.conf <<'EOF'
vm.max_map_count=524288
fs.file-max=131072
EOF
sysctl --system > /dev/null

# Carpeta de despliegue. Jenkins escribe aquí como uid/gid 1000 (dentro del contenedor). En Contabo el uid 1000 es el
# usuario "ubuntu" de la imagen y tu usuario suele ser otro (1001): se comparte por GRUPO (gid 1000), con setgid en
# las carpetas para que todo lo nuevo herede ese grupo.
grupo=$(getent group 1000 | cut -d: -f1)
if [ -n "$grupo" ] && [ "$usuario" != "$(getent passwd 1000 | cut -d: -f1)" ]; then
  usermod -aG "$grupo" "$usuario"
  echo "  $usuario agregado al grupo $grupo (gid 1000): vuelve a entrar por SSH para que tome efecto"
fi

mkdir -p /opt/runagard/deploy/respaldos
chown -R 1000:1000 /opt/runagard
chmod -R g+rwX /opt/runagard
find /opt/runagard -type d -exec chmod g+s {} +
chmod o-rwx /opt/runagard

echo "Listo."
echo "  DOCKER_GID para el .env: $(getent group docker | cut -d: -f3)"
