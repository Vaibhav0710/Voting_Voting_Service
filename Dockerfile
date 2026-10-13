# syntax=docker/dockerfile:1
# Multi-stage build: Maven builds the jar, a slim JRE image runs it as a non-root user.

# ── Build ──────────────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
# Dependencies first, so they are cached until pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ── Runtime ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17.0.13_11-jre-alpine
RUN addgroup -S -g 1001 app && adduser -S -u 1001 -G app app
WORKDIR /app
COPY --from=build --chown=1001:1001 /workspace/target/*.jar app.jar
USER 1001

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8083
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8083/actuator/health/readiness > /dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
