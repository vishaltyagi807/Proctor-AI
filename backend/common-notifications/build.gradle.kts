plugins {
    id("java-library")
}

dependencies {
    api(project(":common-lib"))
    api("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    api("com.fasterxml.jackson.core:jackson-databind")
    compileOnly("org.springframework.boot:spring-boot-starter-webflux")
}
