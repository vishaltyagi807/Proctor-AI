package dev.varshit.proctor.migrator;

import dev.varshit.proctor.testsupport.TestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class DbMigratorApplicationTest {

    @Test
    void applicationMigratesTheConfiguredDatabaseAndStops() throws SQLException {
        String url = TestDatabase.createEmptyDatabase();

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(DbMigratorApplication.class)
                .properties(
                        "MIGRATION_DATABASE_URL=" + url,
                        "POSTGRES_USER=" + TestDatabase.superuser(),
                        "POSTGRES_PASSWORD=" + TestDatabase.superuserPassword(),
                        "AUTHENTICATOR_USER_PASSWORD=" + TestDatabase.AUTHENTICATOR_PASSWORD,
                        "APP_USER_PASSWORD=" + TestDatabase.APP_PASSWORD,
                        "ADMIN_PASSWORD=" + TestDatabase.ADMIN_PASSWORD)
                .run()) {
            Flyway flyway = context.getBean(Flyway.class);
            assertThat(flyway.info().pending()).isEmpty();
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo(TestDatabase.BASELINE_VERSION);
        }
    }
}
