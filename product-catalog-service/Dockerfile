
# Build stage
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

# Copy Gradle wrapper and build configuration
COPY product-catalog-service/gradlew product-catalog-service/gradlew
COPY product-catalog-service/gradle/ product-catalog-service/gradle/
COPY product-catalog-service/build.gradle product-catalog-service/settings.gradle product-catalog-service/

# Copy central protobuf definitions
COPY protos/ protos/

# Copy application source and quality-check configurations
COPY product-catalog-service/src/ product-catalog-service/src/
COPY product-catalog-service/config/ product-catalog-service/config/

WORKDIR /workspace/product-catalog-service

RUN chmod +x gradlew
RUN ./gradlew clean bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

# Run the application as a non-root user
RUN useradd --system --uid 10001 appuser

# Copy the executable JAR, not the plain JAR
COPY --from=build /workspace/product-catalog-service/build/libs/product-catalog-service-0.0.1-SNAPSHOT.jar /app/product-catalog-service.jar

USER 10001

EXPOSE 6566

ENTRYPOINT ["java", "-jar", "/app/product-catalog-service.jar"]
