plugins {
    java
}

System.getProperty("ui.buildDir")?.let { customBuildDir ->
    layout.buildDirectory.set(file(customBuildDir))
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("com.codeborne:selenide:7.18.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

tasks.test {
    useJUnitPlatform()
    // Pass explicit overrides; BrowserSettings supplies defaults for both IDEA and Gradle.
    listOf("ui.baseUrl", "ui.headless", "ui.disableSandbox", "ui.chromeDriver", "ui.chromeBinary").forEach { key ->
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
    // The application state is external to Gradle, so each Run executes the browser tests.
    outputs.upToDateWhen { false }
    testLogging { events("passed", "failed", "skipped") }
}
