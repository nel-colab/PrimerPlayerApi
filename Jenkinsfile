// CI/CD de la API. Se dispara con cada push a GitHub; el entorno sale de la rama:
//   main    → producción (job "runagard-api")          https://runagard.cl
//   develop → staging    (job "runagard-api-staging")  https://staging.runagard.cl
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
        stage('Entorno') {
            steps {
                script {
                    def rama = (env.BRANCH_NAME ?: env.GIT_BRANCH ?: '').replaceFirst('^origin/', '')
                    if (rama == 'main') {
                        env.ENTORNO = 'produccion'
                        env.TAG = 'latest'
                        env.TAG_ANTERIOR = 'anterior'
                        env.COMPOSE = 'docker compose'
                        env.SERVICIO = 'api'
                        env.SONAR_KEY = 'runagard-api'
                        env.SONAR_NOMBRE = 'Primer Player API'
                    } else if (rama == 'develop') {
                        env.ENTORNO = 'staging'
                        env.TAG = 'staging'
                        env.TAG_ANTERIOR = 'staging-anterior'
                        env.COMPOSE = 'docker compose -f docker-compose.staging.yml --env-file .env.staging'
                        env.SERVICIO = 'api-staging'
                        env.SONAR_KEY = 'runagard-api-develop'
                        env.SONAR_NOMBRE = 'Primer Player API (develop)'
                    } else {
                        error("La rama '${rama}' no tiene entorno asignado (solo main y develop se despliegan)")
                    }
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.ENTORNO}"
                    echo "Rama ${rama} → ${env.ENTORNO}"
                }
            }
        }

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
                // Proyecto separado por rama: SonarQube Community no analiza ramas, y así develop no ensucia
                // el historial de main.
                withSonarQubeEnv('sonarqube') {
                    sh './mvnw -B -Pci sonar:sonar -Dsonar.projectKey="$SONAR_KEY" -Dsonar.projectName="$SONAR_NOMBRE"'
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
                    # La imagen desplegada en este entorno pasa a ser "anterior": a ella se vuelve si el despliegue falla.
                    if docker image inspect "$IMAGEN:$TAG" > /dev/null 2>&1; then
                        docker tag "$IMAGEN:$TAG" "$IMAGEN:$TAG_ANTERIOR"
                    fi
                    docker build -t "$IMAGEN:$(echo "$GIT_COMMIT" | cut -c1-8)" -t "$IMAGEN:$TAG" .
                '''
            }
        }

        stage('Desplegar') {
            steps {
                sh '''
                    # Configuración de despliegue versionada en el repo (los .env y los respaldos se quedan en el VPS).
                    # -rlc: copia por contenido, sin fechas/permisos/dueño. Las carpetas pueden ser de otro usuario
                    # del grupo compartido (gid 1000) y Jenkins no podría cambiarles la fecha (rsync -a falla con 23).
                    # Solo main toca la configuración compartida (Caddy, monitoreo, compose de producción);
                    # develop actualiza únicamente el compose de staging.
                    if [ "$ENTORNO" = produccion ]; then
                        rsync -rlc --exclude .env --exclude .env.staging --exclude respaldos/ deploy/ "$DEPLOY_DIR/"
                        chmod +x "$DEPLOY_DIR"/scripts/*.sh 2>/dev/null || true
                    else
                        rsync -c deploy/docker-compose.staging.yml deploy/.env.staging.example "$DEPLOY_DIR/"
                    fi

                    cd "$DEPLOY_DIR"
                    if [ "$ENTORNO" = produccion ]; then
                        $COMPOSE up -d --no-deps "$SERVICIO"
                        $COMPOSE exec -T caddy caddy reload --config /etc/caddy/Caddyfile
                    else
                        $COMPOSE up -d "$SERVICIO"        # levanta también postgres-staging y minio-staging si faltan
                    fi

                    contenedor=$($COMPOSE ps -q "$SERVICIO")
                    for i in $(seq 1 36); do
                        estado=$(docker inspect -f '{{.State.Health.Status}}' "$contenedor")
                        if [ "$estado" = healthy ]; then echo "API sana ($ENTORNO)"; exit 0; fi
                        sleep 5
                    done
                    echo "La API ($ENTORNO) no quedó sana en 3 minutos"
                    $COMPOSE logs --tail 150 "$SERVICIO"
                    exit 1
                '''
            }
            post {
                failure {
                    sh '''
                        cd "$DEPLOY_DIR"
                        if docker image inspect "$IMAGEN:$TAG_ANTERIOR" > /dev/null 2>&1; then
                            echo "Volviendo a la versión anterior ($ENTORNO)"
                            docker tag "$IMAGEN:$TAG_ANTERIOR" "$IMAGEN:$TAG"   # que un reinicio no levante la versión rota
                            $COMPOSE up -d --no-deps "$SERVICIO"
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
