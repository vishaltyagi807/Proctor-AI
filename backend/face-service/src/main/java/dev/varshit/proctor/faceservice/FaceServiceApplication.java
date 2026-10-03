package dev.varshit.proctor.faceservice;

import dev.varshit.proctor.faceservice.config.FaceModelProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(FaceModelProperties.class)
public class FaceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FaceServiceApplication.class, args);
    }
}
