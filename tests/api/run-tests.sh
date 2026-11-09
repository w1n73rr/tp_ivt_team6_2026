#!/bin/sh
# Gradle owns /suite/build. /results is only an export destination, never cleaned.
./gradlew clean test --no-daemon --console=plain
result=$?

# Export diagnostics even when compilation or assertions fail; preserve failure.
mkdir -p /results/reports /results/test-results || exit 1
if [ -d build/reports ]; then
    cp -R build/reports/. /results/reports/ || exit 1
fi
if [ -d build/test-results ]; then
    cp -R build/test-results/. /results/test-results/ || exit 1
fi
printf '%s\n' "$result" > /results/reports/gradle-exit-code.txt || exit 1
exit "$result"
