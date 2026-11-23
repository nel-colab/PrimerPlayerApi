#!/usr/bin/env bash
# Ajustes del VPS que necesitan los contenedores. Ejecutar UNA vez, con sudo, después de instalar Docker:
#   sudo bash deploy/scripts/preparar-servidor.sh
set -euo pipefail

# SonarQube (Elasticsearch interno) no arranca sin esto.
cat > /etc/sysctl.d/99-sonarqube.conf <<'EOF'
vm.max_map_count=524288
fs.file-max=131072
EOF
sysctl --system > /dev/null

# Carpeta de despliegue. Jenkins (uid 1000 dentro del contenedor) sincroniza aquí la carpeta deploy/ del repo.
mkdir -p /opt/runagard/deploy/respaldos
chown -R 1000:1000 /opt/runagard
chmod 750 /opt/runagard

echo "Listo."
echo "  Usuario con uid 1000: $(getent passwd 1000 | cut -d: -f1 || echo 'ninguno')"
echo "  DOCKER_GID para el .env: $(getent group docker | cut -d: -f3)"
