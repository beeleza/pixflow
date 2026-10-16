# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination target/extracted

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S pixflow && adduser -S pixflow -G pixflow

COPY --from=build --chown=pixflow:pixflow /workspace/target/extracted/dependencies/ ./
COPY --from=build --chown=pixflow:pixflow /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=pixflow:pixflow /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=pixflow:pixflow /workspace/target/extracted/application/ ./

USER pixflow

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:${SERVER_PORT:-8080}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
