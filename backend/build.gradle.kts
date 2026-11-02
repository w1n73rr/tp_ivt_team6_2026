plugins {
	java
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.openapi.generator") version "7.20.0"
}

group = "tech.gennf"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
    implementation("software.amazon.awssdk:s3:2.25.50")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
	runtimeOnly("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")

	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")


    //MapStruct
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

    //JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    //yaml
    implementation("org.yaml:snakeyaml")


    // Драйвер PostgreSQL
    runtimeOnly("org.postgresql:postgresql")


    //Tests
    // Spring Boot Test & Security Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")

    // REST Assured для проверки API
    testImplementation("io.rest-assured:rest-assured:5.4.0")

    // JUnit 5 Jupiter
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

    //QueryDSL
    implementation("com.querydsl:querydsl-jpa:5.0.0:jakarta")

    annotationProcessor("com.querydsl:querydsl-apt:5.0.0:jakarta")

    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")

    //Logging
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")

    // Spring Integration для отслеживания папки
    implementation("org.springframework.boot:spring-boot-starter-integration")
    implementation("org.springframework.integration:spring-integration-file")


}

val apiSpec = layout.projectDirectory.file("backend-api.yaml")
val generatedApi = layout.buildDirectory.dir("generated/openapi")

openApiValidate {
	inputSpec.set(apiSpec.asFile.absolutePath)
}

openApiGenerate {
	generatorName.set("spring")
	inputSpec.set(apiSpec.asFile.absolutePath)
	outputDir.set(generatedApi.get().asFile.absolutePath)
	apiPackage.set("tech.genna.TY3.api")
	modelPackage.set("tech.genna.TY3.api.model")
	configOptions.set(mapOf(
		"interfaceOnly" to "true",
		"useTags" to "true",
		"useSpringBoot4" to "true",
		"useJackson3" to "true",
		"openApiNullable" to "false",
		"useBeanValidation" to "true",
		"requestMappingMode" to "api_interface",
		"dateLibrary" to "java8",
		"hideGenerationTimestamp" to "true",
		"useSwaggerUI" to "false"
	))
	globalProperties.set(mapOf(
		"apiTests" to "false",
		"modelTests" to "false",
		"apiDocs" to "false",
		"modelDocs" to "false"
	))
}

sourceSets.main {
	java.srcDir(generatedApi.map { it.dir("src/main/java") })
}

tasks.openApiGenerate {
	dependsOn(tasks.openApiValidate)
}

tasks.compileJava {
	dependsOn(tasks.openApiGenerate)
	options.compilerArgs.add("-parameters")
}

tasks.processResources {
	from(apiSpec) { into("static") }
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.bootJar {
	archiveFileName.set("app.jar")
}
