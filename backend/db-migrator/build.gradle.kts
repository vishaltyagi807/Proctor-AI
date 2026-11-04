plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":db-schema"))
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":test-support"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
