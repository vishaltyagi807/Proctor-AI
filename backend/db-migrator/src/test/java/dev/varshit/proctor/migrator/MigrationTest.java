package dev.varshit.proctor.migrator;

import dev.varshit.proctor.testsupport.TestDatabase;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationTest {

    private static final int INITIAL_MIGRATIONS = 11;

    @Test
    void freshDatabaseAppliesEveryMigration() throws SQLException {
        String url = TestDatabase.createEmptyDatabase();

        MigrateResult result = TestDatabase.migrate(url);

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(INITIAL_MIGRATIONS);
        assertThat(TestDatabase.flyway(url).info().pending()).isEmpty();
        assertThat(scalar(url, "select count(*) from pg_roles where rolname in ('auth_owner', 'authenticator', 'proctor')"))
                .isEqualTo(3);
    }

    @Test
    void secondRunAppliesNothing() throws SQLException {
        String url = TestDatabase.createEmptyDatabase();
        TestDatabase.migrate(url);

        MigrateResult again = TestDatabase.migrate(url);

        assertThat(again.migrationsExecuted).isZero();
    }

    @Test
    void databaseCreatedByTheOldInitScriptsIsBaselined() throws SQLException {
        String url = TestDatabase.createEmptyDatabase();
        TestDatabase.migrate(url);
        execute(url, "drop table flyway_schema_history");

        Flyway flyway = TestDatabase.flyway(url);
        MigrateResult result = flyway.migrate();

        assertThat(result.migrationsExecuted).isZero();
        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied).hasSize(1);
        assertThat(applied[0].getVersion().getVersion()).isEqualTo(TestDatabase.BASELINE_VERSION);
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void seededAdministratorUsesTheConfiguredPassword() throws SQLException {
        String url = TestDatabase.createEmptyDatabase();
        TestDatabase.migrate(url);

        assertThat(scalar(url, "select count(*) from users where email = '" + TestDatabase.ADMIN_EMAIL
                + "' and password = crypt('" + TestDatabase.ADMIN_PASSWORD + "', password)")).isEqualTo(1);
        assertThat(scalar(url, "select count(*) from users where password = crypt('admin123', password)")).isZero();
    }

    @Test
    void passwordsWithQuotesAreStoredLiterally() throws SQLException {
        String url = TestDatabase.createEmptyDatabase();
        String password = "it's a \"quoted\" $secret$";
        Flyway.configure()
                .dataSource(url, TestDatabase.superuser(), TestDatabase.superuserPassword())
                .placeholders(Map.of(
                        "authenticator_password", TestDatabase.AUTHENTICATOR_PASSWORD,
                        "app_password", TestDatabase.APP_PASSWORD,
                        "admin_password", password))
                .load()
                .migrate();

        assertThat(scalar(url, "select count(*) from users where password = crypt($q$" + password + "$q$, password)"))
                .isEqualTo(1);
    }

    private static long scalar(String url, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, TestDatabase.superuser(), TestDatabase.superuserPassword());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void execute(String url, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, TestDatabase.superuser(), TestDatabase.superuserPassword());
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
