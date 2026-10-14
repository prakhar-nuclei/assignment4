
# Build stage
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

# Copy Gradle wrapper and build configuration
COPY order-service/gradlew order-service/gradlew
COPY order-service/gradle/ order-service/gradle/
COPY order-service/build.gradle order-service/settings.gradle order-service/

# Copy central protobuf definitions
COPY protos/ protos/

# Copy application source and PMD configuration
COPY order-service/src/ order-service/src/
COPY order-service/config/ order-service/config/

WORKDIR /workspace/order-service

RUN chmod +x gradlew
RUN ./gradlew clean bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

# Run as a non-root user
RUN useradd --system --uid 10001 appuser

# Copy the executable JAR
COPY --from=build /workspace/order-service/build/libs/order-service-0.0.1-SNAPSHOT.jar /app/order-service.jar

USER 10001

EXPOSE 6567

ENTRYPOINT ["java", "-jar", "/app/order-service.jar"]
