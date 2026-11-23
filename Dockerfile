# syntax=docker/dockerfile:1
# Imagen de la API. Jenkins la construye después de pasar las pruebas: docker build -t runagard/api:<commit> .

# ---------- compilación ----------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
# Dependencias en una capa aparte (y en caché de BuildKit): solo se descargan de nuevo si cambia el pom.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/primer-player-api-*.jar extract --layers --launcher --destination /app

# ---------- ejecución ----------
FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
 && groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app
# Capas de Spring Boot: las dependencias casi nunca cambian; el código de la app sí.
COPY --from=build /app/dependencies/ ./
COPY --from=build /app/spring-boot-loader/ ./
COPY --from=build /app/snapshot-dependencies/ ./
COPY --from=build /app/application/ ./
USER app
ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=UTC \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080 8081
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8081/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
