plugins { java }
repositories { mavenCentral() }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testImplementation("io.rest-assured:rest-assured:5.4.0")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.17.3")
    constraints { testImplementation("commons-logging:commons-logging:1.4.0") }
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}
tasks.test {
    useJUnitPlatform {
        if (project.hasProperty("attemptPassing")) includeTags("attempt-passing")
        else includeTags("attempt-result")
    }
    outputs.upToDateWhen { false }
    testLogging { events("passed", "failed", "skipped") }
}
tasks.withType<JavaCompile> { options.encoding = "UTF-8" }
