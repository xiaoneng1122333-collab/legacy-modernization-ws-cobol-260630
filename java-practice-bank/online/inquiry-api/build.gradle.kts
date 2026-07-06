plugins {
    id("spring-boot-conventions")
}

dependencies {
    // Reuse domain primitives + MyBatis from common
    implementation(project(":common:common-domain"))
    implementation(project(":common:common-mybatis"))
    // 検索 API 用: 既存の customer-service / account-service のマッパーを流用
    // Masters モジュールはオンライン API から直接依存し、 Mapper を直接オートワイアする
    implementation(project(":masters:customer-service"))
    implementation(project(":masters:account-service"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation(project(":common:common-test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
