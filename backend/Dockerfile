FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

COPY gradle /app/gradle
COPY gradlew /app/
RUN chmod +x /app/gradlew
COPY build.gradle.kts settings.gradle* /app/
COPY backend-api.yaml /app/

RUN ./gradlew dependencies --no-daemon

COPY src /app/src
RUN ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:25-jre AS runtime-base
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN useradd -m springuser && chown -R springuser:springuser /app
USER springuser

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseG1GC", "-jar", "app.jar"]

# CI packages the JAR already built and tested by the backend job.
FROM runtime-base AS ci-runtime
COPY app.jar app.jar

# Default local build still compiles everything from source.
FROM runtime-base AS runtime
COPY --from=builder /app/build/libs/*.jar app.jar
