package dev.varshit.proctor.persistence.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.r2dbc.core.DatabaseClient;

import java.util.ArrayList;
import java.util.List;

public class DatabaseRoleGuard implements ApplicationRunner {

    private final DatabaseClient client;

    public DatabaseRoleGuard(DatabaseClient client) {
        this.client = client;
    }

    @Override
    public void run(ApplicationArguments args) {
        String violation = client.sql(
                        "select current_user as name, r.rolsuper, r.rolbypassrls, r.rolcreaterole, r.rolcreatedb,"
                                + " r.rolreplication from pg_roles r where r.rolname = current_user")
                .map(row -> describe(
                        row.get("name", String.class),
                        Boolean.TRUE.equals(row.get("rolsuper", Boolean.class)),
                        Boolean.TRUE.equals(row.get("rolbypassrls", Boolean.class)),
                        Boolean.TRUE.equals(row.get("rolcreaterole", Boolean.class)),
                        Boolean.TRUE.equals(row.get("rolcreatedb", Boolean.class)),
                        Boolean.TRUE.equals(row.get("rolreplication", Boolean.class))))
                .one()
                .block();
        if (violation != null && !violation.isEmpty()) {
            throw new IllegalStateException(violation);
        }
    }

    private String describe(String name, boolean superuser, boolean bypassRls, boolean createRole,
                            boolean createDb, boolean replication) {
        List<String> privileges = new ArrayList<>();
        if (superuser) {
            privileges.add("SUPERUSER");
        }
        if (bypassRls) {
            privileges.add("BYPASSRLS");
        }
        if (createRole) {
            privileges.add("CREATEROLE");
        }
        if (createDb) {
            privileges.add("CREATEDB");
        }
        if (replication) {
            privileges.add("REPLICATION");
        }
        return privileges.isEmpty() ? "" : "Refusing to start: database role '" + name
                + "' has elevated privileges " + privileges + ". Services must use a role without security bypass.";
    }
}
