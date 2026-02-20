##############################################################################
# Multi-stage Dockerfile for Cache Service
#
# Stage 1 (builder): compiles the Maven project and produces the fat JAR.
# Stage 2 (runtime): minimal JRE image that only ships the runnable artifact.
#
# Build:  docker build -t cache-service:latest .
# Run:    docker run -p 8080:8080 --env-file .env cache-service:latest
##############################################################################

# ─── Stage 1: Build ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build

# Copy dependency manifests first to leverage Docker layer cache.
# Dependencies are only re-downloaded when pom.xml changes.
COPY pom.xml .
COPY .mvn/ .mvn/

# Download dependencies in a dedicated layer (offline-friendly in CI)
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B -q

# Copy source and build, skipping integration tests for the image build
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -B -q -DskipTests \
    && mv target/cache-service-*.jar target/app.jar

# ─── Stage 2: Runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Security: run as a non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy only the fat JAR from the builder stage
COPY --from=builder /build/target/app.jar app.jar

# Create log directory and set permissions
RUN mkdir -p logs && chown -R appuser:appgroup /app

USER appuser

# Expose HTTP port (matches server.port default)
EXPOSE 8080

# JVM tuning:
#   -XX:+UseContainerSupport   – respect cgroup memory limits (default in JDK 11+)
#   -XX:MaxRAMPercentage=75    – use up to 75% of container RAM for the heap
#   -XX:+UseG1GC               – low-pause garbage collector
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
