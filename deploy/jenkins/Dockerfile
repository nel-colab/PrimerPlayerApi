# Jenkins con todo lo que necesitan los pipelines de la tienda:
#   - JDK 25 (viene en la imagen lts-jdk25): compila y prueba la API con ./mvnw
#   - Node 24: pruebas y build del frontend Angular
#   - Docker CLI + compose + buildx: construye imágenes y despliega en el mismo VPS (socket montado)
#   - Plugins y configuración como código (casc/casc.yaml): no hay asistente de instalación
FROM jenkins/jenkins:lts-jdk25

USER root
RUN apt-get update \
 && apt-get install -y --no-install-recommends rsync curl \
 && rm -rf /var/lib/apt/lists/*

COPY --from=docker:29-cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker:29-cli /usr/local/libexec/docker/cli-plugins/ /usr/local/libexec/docker/cli-plugins/

COPY --from=node:24-trixie-slim /usr/local/bin/node /usr/local/bin/node
COPY --from=node:24-trixie-slim /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
 && ln -s /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx \
 && node --version && npm --version && docker --version && docker compose version

USER jenkins
COPY --chown=jenkins:jenkins plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt
