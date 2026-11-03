FROM eclipse-temurin:17-jdk
WORKDIR /suite
COPY ui-tests/gradle /suite/gradle
COPY ui-tests/gradlew /suite/gradlew
COPY api-tests/settings.gradle.kts api-tests/build.gradle.kts /suite/
RUN chmod +x /suite/gradlew
COPY api-tests/src /suite/src
COPY api-tests/run-tests.sh /suite/run-tests.sh
RUN sed -i 's/\r$//' /suite/run-tests.sh
CMD ["sh", "/suite/run-tests.sh"]
