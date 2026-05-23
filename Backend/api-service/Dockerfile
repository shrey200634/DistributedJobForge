# Multi-stage build for smaller final image
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy gradle wrapper and config first (for layer caching)
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

# Copy source
COPY src src

# Build the JAR (skip tests for faster builds)
RUN chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
