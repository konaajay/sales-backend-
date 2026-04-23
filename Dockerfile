# ─────────────────────────────────────────────
# Stage 1: Build the JAR
# ─────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper & POM first for dependency caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Make mvnw executable
RUN chmod +x mvnw

# Download dependencies (cached layer unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the fat JAR, skip tests for faster builds
RUN ./mvnw package -DskipTests -B

# ─────────────────────────────────────────────
# Stage 2: Run the application
# ─────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

# Add a non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# Create uploads directory and give ownership to spring user
RUN mkdir -p /app/uploads && chown -R spring:spring /app

# Copy only the built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

USER spring

# Render uses the PORT environment variable; default 8081 matches application.properties
EXPOSE 8081

# JVM tuning for container environments (Render free tier has 512 MB RAM)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
