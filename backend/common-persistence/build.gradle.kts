plugins {
    id("java-library")
}

dependencies {
    api(project(":common-lib"))
    api(project(":common-security"))
    api("org.springframework.boot:spring-boot-starter-data-r2dbc")
    api("org.postgresql:r2dbc-postgresql")
}
