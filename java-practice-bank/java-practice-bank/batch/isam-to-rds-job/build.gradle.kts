// java-practice-bank/batch/isam-to-rds-job/build.gradle.kts
plugins {
    id("spring-boot-conventions")
}

dependencies {
    implementation(project(":common:common-domain"))
    implementation(project(":common:common-batch"))
    implementation(project(":common:common-mybatis"))
    implementation("org.springframework.boot:spring-boot-starter-batch")
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.3")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation(project(":common:common-test"))
    testImplementation("org.springframework.batch:spring-batch-test")
}
