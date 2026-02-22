# ================================================================
# Multi-stage Dockerfile for Cache Service
# Stage 1: Build with Maven
# Stage 2: Extract layered JAR for optimal layer caching
# Stage 3: Minimal JRE runtime image
# ================================================================

# ----------------------------------------------------------------
# Stage 1: Build
# ----------------------------------------------------------------
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and POM first (for dependency caching)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (cached unless pom.xml changes)
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B -q

# Copy source and build (skip integration tests in Docker build)
COPY src src
RUN ./mvnw package -B -q -DskipTests

# ----------------------------------------------------------------
# Stage 2: Extract layered JAR
# Layered JARs enable fine-grained Docker layer caching:
# - dependencies (rarely change)
# - spring-boot-loader (rarely changes)
# - snapshot-dependencies (occasionally change)
# - application (changes every build)
# ----------------------------------------------------------------
FROM eclipse-temurin:17-jdk-alpine AS extractor

WORKDIR /extract
COPY --from=builder /build/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ----------------------------------------------------------------
# Stage 3: Runtime
# ----------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine AS runtime

# Security: run as non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy layered JAR contents (most-stable layers first for cache efficiency)
COPY --from=extractor /extract/dependencies/ ./
COPY --from=extractor /extract/spring-boot-loader/ ./
COPY --from=extractor /extract/snapshot-dependencies/ ./
COPY --from=extractor /extract/application/ ./

# Create directory for logs
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app/logs

USER appuser

# Application port
EXPOSE 8080
# Management/Actuator port
EXPOSE 8081

# Health check using Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8081/actuator/health | grep -q '"status":"UP"' || exit 1

# JVM tuning for containerized environments
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+OptimizeStringConcat \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.backgroundpreinitializer.ignore=true"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
