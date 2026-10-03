import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

plugins {
    id("java")
    id("org.springframework.boot") version "3.5.11" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.google.protobuf") version "0.9.5" apply false
}

group = "dev.varshit.proctor"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val springCloudVersion = "2025.0.0"

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {

    group = rootProject.group
    version = rootProject.version

    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    extensions.configure<DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.11")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
        }
    }

    dependencies {
        compileOnly("org.projectlombok:lombok:1.18.42")
        annotationProcessor("org.projectlombok:lombok:1.18.42")

        implementation("org.mapstruct:mapstruct:1.6.3")
        annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
        annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
    }

    tasks.withType<JavaCompile> {
        options.annotationProcessorPath = configurations.annotationProcessor.get()
    }

    tasks.test {
        useJUnitPlatform()
    }

}

tasks.jar {
    enabled = false
}

subprojects {
    pluginManager.withPlugin("org.springframework.boot") {
        tasks.named<Jar>("jar") { enabled = false }
        tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
            archiveFileName.set("${project.name}.jar")
        }
    }
}
