plugins {
	java
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.murdermystery"
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
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	implementation("org.springframework.boot:spring-boot-flyway")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("com.networknt:json-schema-validator:1.5.6")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
	// DOCKER_HOST가 없고 /var/run/docker.sock도 없을 때만 개입 (Docker Desktop·Linux는 해당 없음).
	// 개발 환경별 소켓 자동 감지: OrbStack → Colima 순서.
	if (System.getenv("DOCKER_HOST") == null && !File("/var/run/docker.sock").exists()) {
		val home = System.getProperty("user.home")
		val candidates = listOf(
			"$home/.orbstack/run/docker.sock",
			"$home/.colima/default/docker.sock",
		)
		val detected = candidates.firstOrNull { File(it).exists() }
		if (detected != null) environment("DOCKER_HOST", "unix://$detected")
	}
}
