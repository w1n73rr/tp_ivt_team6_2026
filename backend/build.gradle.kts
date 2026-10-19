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
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
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
	testImplementation("io.rest-assured:rest-assured:5.5.0")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
