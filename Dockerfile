# Build stage
FROM gradle:8.5-jdk17-alpine AS build

WORKDIR /app

# Copy Gradle files for dependency caching
COPY build.gradle settings.gradle ./
COPY gradle gradle

# Download dependencies (cached layer)
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY src src

# Build application (skip tests for faster builds)
RUN gradle clean bootJar --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy JAR from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]