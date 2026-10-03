plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":common-persistence"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(project(":common-monitoring"))
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("software.amazon.awssdk:s3:2.29.52")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
