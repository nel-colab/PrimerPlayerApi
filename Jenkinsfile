// CI/CD de la API (job "runagard-api", rama main). Se dispara con cada push a GitHub.
// Pruebas (Testcontainers) → SonarQube + quality gate → imagen Docker → despliegue con vuelta atrás automática.

pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 45, unit: 'MINUTES')
    }

    environment {
        JAVA_HOME = '/opt/java/openjdk'       // JDK 25 de la imagen jenkins/jenkins:lts-jdk25
        MAVEN_OPTS = '-Xmx768m'
        DEPLOY_DIR = '/opt/runagard/deploy'
        IMAGEN = 'runagard/api'
    }

    stages {
        stage('Pruebas') {
            steps {
                sh 'chmod +x mvnw && ./mvnw -B -Pci verify'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('SonarQube') {
            steps {
                withSonarQubeEnv('sonarqube') {
                    sh './mvnw -B -Pci sonar:sonar -Dsonar.projectKey=runagard-api -Dsonar.projectName="Primer Player API"'
                }
            }
        }

        stage('Quality gate') {
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Imagen') {
            steps {
                sh '''
                    # La imagen en producción pasa a ser "anterior": a ella se vuelve si el despliegue falla.
                    if docker image inspect "$IMAGEN:latest" > /dev/null 2>&1; then
                        docker tag "$IMAGEN:latest" "$IMAGEN:anterior"
                    fi
                    docker build -t "$IMAGEN:$(echo "$GIT_COMMIT" | cut -c1-8)" -t "$IMAGEN:latest" .
                '''
            }
        }

        stage('Desplegar') {
            steps {
                sh '''
                    # Configuración de despliegue versionada en el repo (el .env y los respaldos se quedan en el VPS).
                    # -rlc: copia por contenido, sin fechas/permisos/dueño. Las carpetas pueden ser de otro usuario
                    # del grupo compartido (gid 1000) y Jenkins no podría cambiarles la fecha (rsync -a falla con 23).
                    rsync -rlc --exclude .env --exclude respaldos/ deploy/ "$DEPLOY_DIR/"
                    chmod +x "$DEPLOY_DIR"/scripts/*.sh 2>/dev/null || true

                    cd "$DEPLOY_DIR"
                    docker compose up -d --no-deps api
                    docker compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile

                    contenedor=$(docker compose ps -q api)
                    for i in $(seq 1 36); do
                        estado=$(docker inspect -f '{{.State.Health.Status}}' "$contenedor")
                        if [ "$estado" = healthy ]; then echo "API sana"; exit 0; fi
                        sleep 5
                    done
                    echo "La API no quedó sana en 3 minutos"
                    docker compose logs --tail 150 api
                    exit 1
                '''
            }
            post {
                failure {
                    sh '''
                        cd "$DEPLOY_DIR"
                        if docker image inspect "$IMAGEN:anterior" > /dev/null 2>&1; then
                            echo "Volviendo a la versión anterior"
                            docker tag "$IMAGEN:anterior" "$IMAGEN:latest"   # que un reinicio no levante la versión rota
                            docker compose up -d --no-deps api
                        fi
                    '''
                }
            }
        }
    }

    post {
        always {
            sh 'docker image prune -f > /dev/null || true'
            cleanWs()
        }
    }
}
