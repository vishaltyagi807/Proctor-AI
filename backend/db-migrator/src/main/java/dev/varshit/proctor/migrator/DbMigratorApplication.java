package dev.varshit.proctor.migrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DbMigratorApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(DbMigratorApplication.class, args)));
    }
}
