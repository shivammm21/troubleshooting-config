/**
 * Backend module – Java Spring Boot library.
 *
 * This module is NOT packaged as a standalone Spring Boot fat-jar.
 * It is consumed as a dependency by the desktop-app module, which starts
 * the Spring application context programmatically inside the same JVM.
 *
 * We deliberately avoid applying the Spring Boot application plugin here
 * so that no bootJar / bootRun tasks conflict with the desktop launcher.
 */

plugins {
    java
    id("io.spring.dependency-management") version "1.1.6"
}

// Import the Spring Boot BOM for consistent dependency versions without
// making this module a runnable Spring Boot application.
dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.5")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Spring needs the -parameters flag so it can read method parameter names at
// runtime for @PathVariable, @RequestParam etc. without requiring @PathVariable("name").
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

// Redirect build outputs to a path that won't conflict with IDE language-server
// file locks on the default build/classes directory. This allows Gradle to
// recompile cleanly even while the IDE has the project open.
layout.buildDirectory.set(rootProject.layout.projectDirectory.dir("backend-build"))

dependencies {
    // Core Spring Boot starters – brought in as a regular implementation dependency.
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Logging – SLF4J + Logback already included via spring-boot-starter.

    // Jackson for JSON serialisation.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

    // Docker Engine API client (Unix socket / npipe / DOCKER_HOST).
    implementation("com.github.docker-java:docker-java:3.4.1")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:3.4.1")

    // ---- Test dependencies ------------------------------------------------
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Keep test runs out of the developer's real ~/.stackpilot/state.json so the
    // desktop history only ever shows genuine troubleshooting sessions.
    systemProperty(
        "stackpilot.state.dir",
        layout.buildDirectory.dir("test-state").get().asFile.absolutePath
    )
}

// Produce a plain jar (not a fat jar) so the desktop module can depend on it.
tasks.jar {
    enabled = true
}
