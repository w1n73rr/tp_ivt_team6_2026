plugins { java }
repositories { mavenCentral() }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testImplementation("io.rest-assured:rest-assured:5.4.0")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.17.3")
    testImplementation("org.apache.pdfbox:pdfbox:3.0.8")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}
tasks.test {
    useJUnitPlatform()
    outputs.upToDateWhen { false }
    testLogging { events("passed", "failed", "skipped") }
}
tasks.withType<JavaCompile> { options.encoding = "UTF-8" }
