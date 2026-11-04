plugins {
    id("java-library")
}

dependencies {
    api(project(":db-schema"))
    api("org.testcontainers:postgresql")
    api("org.testcontainers:junit-jupiter")
    api("org.junit.jupiter:junit-jupiter-api")
    api("org.flywaydb:flyway-core")
    api("org.flywaydb:flyway-database-postgresql")
    api("org.postgresql:postgresql")
}
