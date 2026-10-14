# Build stage
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

# Copy Gradle wrapper and build configuration
COPY user-service/gradlew user-service/gradlew
COPY user-service/gradle user-service/gradle
COPY user-service/build.gradle user-service/settings.gradle user-service/

# Copy central protobuf definitions and application source
COPY protos/ protos/
COPY user-service/src/ user-service/src/
COPY user-service/config/ user-service/config/

WORKDIR /workspace/user-service

RUN chmod +x gradlew
RUN ./gradlew clean bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --system --uid 10001 appuser

COPY --from=build /workspace/user-service/build/libs/user-service.jar /app/user-service.jar

USER 10001

EXPOSE 6565

ENTRYPOINT ["java", "-jar", "/app/user-service.jar"]