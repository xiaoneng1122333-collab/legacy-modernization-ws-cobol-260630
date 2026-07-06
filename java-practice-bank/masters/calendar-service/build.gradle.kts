plugins {
    id("spring-boot-conventions")
}

dependencies {
    implementation(project(":common:common-domain"))
    implementation(project(":common:common-mybatis"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    runtimeOnly("org.postgresql:postgresql")
}
