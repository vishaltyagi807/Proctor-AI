import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

plugins {
    id("org.springframework.boot")
}

extensions.configure<DependencyManagementExtension> {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:1.1.8")
    }
}

dependencies {
    implementation(project(":common-persistence"))
    implementation(project(":common-notifications"))
    implementation(project(":common-storage"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(project(":common-monitoring"))
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("com.microsoft.onnxruntime:onnxruntime:1.28.0")
    implementation("org.apache.poi:poi-ooxml:5.5.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
