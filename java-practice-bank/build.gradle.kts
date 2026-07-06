// java-practice-bank/build.gradle.kts
plugins {
    java
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management") apply false
}

allprojects {
    group = "com.practicebank"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    // Spring Boot 3.4 BOM pins Testcontainers to 1.19.8, whose docker-java
    // client defaults to API v1.32. The Docker daemon (API ≥ 1.40) rejects this.
    // Use the dependency-management plugin's own override (ext property)
    // because resolutionStrategy.force() is overridden by the BOM plugin.
    extra["testcontainers.version"] = "1.20.3"
    extra["docker-java.version"] = "3.4.0"

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-parameters", "-Xlint:unchecked", "-Xlint:deprecation"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }
        // Testcontainers / docker-java: explicitly set Docker connection.
        // docker-java 3.4.x defaults to a hardcoded API version that may be
        // older than what the daemon accepts. Setting these JVM args forces
        // the correct API version and socket path.
        jvmArgs("-Ddocker.host=unix:///var/run/docker.sock")
        jvmArgs("-Dapi.version=1.41")
        environment("DOCKER_HOST", "unix:///var/run/docker.sock")
        environment("DOCKER_API_VERSION", "1.41")
    }
}
