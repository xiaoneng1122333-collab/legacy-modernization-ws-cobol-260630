plugins {
    id("java-conventions")
}

dependencies {
    implementation(project(":common:common-domain"))
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.3")
    implementation("org.springframework:spring-jdbc")
}
