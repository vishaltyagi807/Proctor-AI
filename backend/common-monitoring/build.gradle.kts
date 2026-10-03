plugins {
    id("java-library")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    api("io.micrometer:micrometer-core")
    api("com.fasterxml.jackson.core:jackson-databind")
}
