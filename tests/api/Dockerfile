FROM eclipse-temurin:17-jdk
WORKDIR /suite
COPY tests/ui/gradle /suite/gradle
COPY tests/ui/gradlew /suite/gradlew
COPY tests/api/settings.gradle.kts tests/api/build.gradle.kts /suite/
RUN chmod +x /suite/gradlew
COPY tests/api/src /suite/src
COPY tests/api/run-tests.sh /suite/run-tests.sh
RUN sed -i 's/\r$//' /suite/run-tests.sh
CMD ["sh", "/suite/run-tests.sh"]
