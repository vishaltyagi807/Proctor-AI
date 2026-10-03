plugins {
    id("org.springframework.boot")
}

dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(project(":common-monitoring"))
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-server")
}
