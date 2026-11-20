FROM eclipse-temurin:25-jdk
WORKDIR /suite
COPY backend .
COPY api-tests/attempt-result/repositories.gradle /tmp/repositories.gradle
RUN chmod +x gradlew
CMD ["sh", "./gradlew", "generationUiServer", "-I", "/tmp/repositories.gradle", "--no-daemon", "--no-watch-fs"]
