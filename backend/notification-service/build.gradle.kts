plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":common-persistence"))
    implementation(project(":common-notifications"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(project(":common-monitoring"))
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
