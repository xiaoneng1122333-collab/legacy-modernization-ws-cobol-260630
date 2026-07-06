plugins {
    id("java-conventions")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.5")
    }
}

dependencies {
    implementation(project(":common:common-domain"))
    implementation("org.springframework.batch:spring-batch-core")
    implementation("org.springframework:spring-context")
}
