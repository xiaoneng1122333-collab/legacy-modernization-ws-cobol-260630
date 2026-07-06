// java-practice-bank/buildSrc/src/main/kotlin/spring-boot-conventions.gradle.kts
plugins {
    id("java-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
