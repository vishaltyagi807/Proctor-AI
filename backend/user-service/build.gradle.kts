plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":common-customfields"))
    implementation(project(":common-notifications"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(project(":common-monitoring"))
    implementation(project(":common-storage"))
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("com.opencsv:opencsv:5.12.0")
    implementation("org.apache.poi:poi-ooxml:5.5.1")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
