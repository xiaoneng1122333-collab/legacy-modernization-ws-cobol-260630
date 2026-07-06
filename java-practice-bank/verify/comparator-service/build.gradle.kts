plugins { id("spring-boot-conventions") }

dependencies {
    implementation(project(":common:common-domain"))
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation(project(":common:common-test"))
}
