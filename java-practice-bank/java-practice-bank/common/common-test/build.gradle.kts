plugins {
    id("java-conventions")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.5")
    }
}

dependencies {
    implementation("org.testcontainers:testcontainers:1.20.3")
    implementation("org.testcontainers:postgresql:1.20.3")
    implementation("org.junit.jupiter:junit-jupiter")
}
