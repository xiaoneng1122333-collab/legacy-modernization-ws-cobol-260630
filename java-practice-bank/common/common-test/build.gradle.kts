plugins {
    id("java-conventions")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.5")
    }
}

dependencies {
    api("org.testcontainers:testcontainers:1.20.3")
    api("org.testcontainers:postgresql:1.20.3")
    api("org.junit.jupiter:junit-jupiter")
}

// Force-resolve Testcontainers to 1.20.x because Spring Boot 3.4.x BOM pins
// 1.19.8, whose docker-java client defaults to API v1.32 (daemon ≥ 1.40 rejects it).
configurations.all {
    resolutionStrategy {
        force("org.testcontainers:testcontainers:1.20.3")
        force("org.testcontainers:postgresql:1.20.3")
        force("org.testcontainers:docker-java-api:3.4.0")
        force("org.testcontainers:docker-java-transport-httpclient5:1.20.3")
    }
}

// Docker env vars for Testcontainers are set at root level
