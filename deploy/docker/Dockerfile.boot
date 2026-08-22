# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /src
COPY pom.xml .
COPY bluedock-common bluedock-common
COPY bluedock-auth bluedock-auth
COPY bluedock-user bluedock-user
COPY bluedock-org bluedock-org
COPY bluedock-project bluedock-project
COPY bluedock-task bluedock-task
COPY bluedock-messenger bluedock-messenger
COPY bluedock-file bluedock-file
COPY bluedock-report bluedock-report
COPY bluedock-system bluedock-system
COPY bluedock-search bluedock-search
COPY bluedock-assistant bluedock-assistant
COPY bluedock-realtime bluedock-realtime
COPY bluedock-worker-notify bluedock-worker-notify
COPY bluedock-worker-index bluedock-worker-index
COPY bluedock-boot bluedock-boot
RUN apk add --no-cache maven \
    && mvn -pl bluedock-boot -am package -DskipTests -q

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S bluedock && adduser -S bluedock -G bluedock \
    && mkdir -p /app/data/uploads /app/data/secrets \
    && chown -R bluedock:bluedock /app
WORKDIR /app
COPY --from=builder /src/bluedock-boot/target/bluedock-boot-*.jar app.jar
COPY CHANGELOG.md /app/CHANGELOG.md
USER bluedock
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
