# syntax=docker/dockerfile:1
# ====================================================================
# Faculty Achievement Portal — backend container image (multi-stage).
#
# "Multi-stage" = we build the app in a big image that has the compiler,
# then copy ONLY the finished jar into a small runtime image. The final
# image ships no source code and no build tools, so it is smaller and has
# a smaller attack surface.
# ====================================================================

# ---- Stage 1: build ----------------------------------------------------
# Full JDK 21 + Maven. This stage compiles the code and runs the unit tests.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy only the build descriptor first and pre-download dependencies. Docker
# caches this layer, so editing source code later does NOT re-download the whole
# Maven dependency tree — rebuilds are much faster.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Now bring in the source and build the runnable jar. Unit tests run here and
# act as a build gate: if a test fails, the image build fails.
COPY src ./src
RUN mvn -B clean package

# ---- Stage 2: runtime --------------------------------------------------
# JRE only (no compiler, no Maven). Smaller and safer to run in production.
FROM eclipse-temurin:21-jre AS runtime

# curl is used only by the container HEALTHCHECK below to probe /actuator/health.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as an unprivileged user (defense in depth: if the app is ever exploited,
# it is not running as root inside the container).
RUN useradd --system --uid 10001 --create-home appuser
WORKDIR /app

# Copy just the repackaged Spring Boot jar from the build stage.
COPY --from=build /build/target/*.jar app.jar

# Uploaded PDF proofs live here; docker-compose mounts a persistent volume at
# /app/uploads so files survive container restarts and redeploys. /app/logs is
# created here too so a mounted logs volume inherits appuser ownership (Docker
# copies the mountpoint's ownership onto a newly-created named volume).
RUN mkdir -p /app/uploads /app/logs && chown -R appuser:appuser /app
USER appuser

EXPOSE 8080

# The container is reported "healthy" only when Spring's health endpoint returns
# UP — and that check includes database connectivity. docker-compose uses this to
# gate startup order and to restart the container if it becomes unhealthy.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# Container-aware memory: the JVM sizes its heap from the container limit.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
