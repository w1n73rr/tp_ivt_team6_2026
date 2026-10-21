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
    filter { includeTestsMatching("*.MaterialUploadUiTest") }
    systemProperty("ui.baseUrl", System.getProperty("ui.baseUrl", "http://localhost:3000"))
    systemProperty("ui.browser", System.getProperty("ui.browser", "chrome"))
    systemProperty("ui.headless", System.getProperty("ui.headless", "true"))
    systemProperty("ui.disableSandbox", System.getProperty("ui.disableSandbox", "false"))
    testLogging { events("passed", "failed", "skipped") }
}
