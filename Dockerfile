# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9.5-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies first (cached layer — only re-runs if pom.xml changes)
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

c
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/target/rate-limiter-service-*.jar app.jar

EXPOSE 8080

# JVM flags tuned for containerised environments
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
