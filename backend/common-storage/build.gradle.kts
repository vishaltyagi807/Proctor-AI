plugins {
    id("java-library")
}

dependencies {
    api(project(":common-lib"))
    api("org.springframework.boot:spring-boot-starter-webflux")
}
