package dev.varshit.proctor.storageservice;

import dev.varshit.proctor.storageservice.config.S3Properties;
import dev.varshit.proctor.storageservice.config.StorageLimits;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({S3Properties.class, StorageLimits.class})
public class StorageServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StorageServiceApplication.class, args);
    }
}
