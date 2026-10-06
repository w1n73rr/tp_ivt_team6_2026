FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

COPY gradle /app/gradle
COPY gradlew /app/
COPY build.gradle.kts settings.gradle* /app/

RUN ./gradlew dependencies --no-daemon

COPY src /app/src
RUN ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd -m springuser && chown -R springuser:springuser /app
USER springuser

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseG1GC", "-jar", "app.jar"]
