plugins {
    id("java-conventions")
}

dependencies {
    implementation(project(":common:common-domain"))
    api("org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.3")
    api("org.springframework:spring-jdbc")
}
