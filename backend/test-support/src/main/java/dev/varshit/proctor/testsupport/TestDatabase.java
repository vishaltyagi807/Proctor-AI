package dev.varshit.proctor.testsupport;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class TestDatabase {

    public static final String ADMIN_EMAIL = "admin@college.com";
    public static final String ADMIN_PASSWORD = "admin-test-password";
    public static final String AUTHENTICATOR_USER = "authenticator";
    public static final String AUTHENTICATOR_PASSWORD = "authenticator-test-password";
    public static final String APP_USER = "proctor";
    public static final String APP_PASSWORD = "app-test-password";
    public static final String BASELINE_VERSION = "1.11";

    private static final DockerImageName IMAGE = DockerImageName.parse("pgvector/pgvector:pg16")
            .asCompatibleSubstituteFor("postgres");
    private static final String DATABASE = "proctor";
    private static final String SUPERUSER = "proctor_admin";
    private static final String SUPERUSER_PASSWORD = "proctor-admin-test-password";
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private static PostgreSQLContainer<?> container;

    private TestDatabase() {
    }

    public static synchronized PostgreSQLContainer<?> container() {
        if (container == null) {
            PostgreSQLContainer<?> started = new PostgreSQLContainer<>(IMAGE)
                    .withDatabaseName(DATABASE)
                    .withUsername(SUPERUSER)
                    .withPassword(SUPERUSER_PASSWORD);
            started.start();
            migrate(started.getJdbcUrl());
            container = started;
        }
        return container;
    }

    public static Map<String, String> placeholders() {
        return Map.of(
                "authenticator_password", AUTHENTICATOR_PASSWORD,
                "app_password", APP_PASSWORD,
                "admin_password", ADMIN_PASSWORD);
    }

    public static Flyway flyway(String jdbcUrl) {
        return Flyway.configure()
                .dataSource(jdbcUrl, SUPERUSER, SUPERUSER_PASSWORD)
                .baselineOnMigrate(true)
                .baselineVersion(BASELINE_VERSION)
                .placeholders(placeholders())
                .load();
    }

    public static MigrateResult migrate(String jdbcUrl) {
        return flyway(jdbcUrl).migrate();
    }

    public static String jdbcUrl() {
        return container().getJdbcUrl();
    }

    public static String r2dbcUrl() {
        PostgreSQLContainer<?> db = container();
        return "r2dbc:postgresql://" + db.getHost() + ":" + db.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + DATABASE;
    }

    public static String superuser() {
        return SUPERUSER;
    }

    public static String superuserPassword() {
        return SUPERUSER_PASSWORD;
    }

    public static Connection connectAsSuperuser() throws SQLException {
        return DriverManager.getConnection(jdbcUrl(), SUPERUSER, SUPERUSER_PASSWORD);
    }

    public static Connection connect(String user, String password) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(), user, password);
    }

    public static String createEmptyDatabase() throws SQLException {
        String name = "scratch_" + COUNTER.incrementAndGet();
        try (Connection connection = connectAsSuperuser(); Statement statement = connection.createStatement()) {
            statement.execute("create database " + name);
        }
        return jdbcUrl().replace("/" + DATABASE, "/" + name);
    }
}
