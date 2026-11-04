package dev.varshit.proctor.migrator;

import dev.varshit.proctor.testsupport.TestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RowLevelSecurityTest {

    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    private static UUID admin;
    private static UUID studentA;
    private static UUID studentB;
    private static UUID teacher;
    private static UUID science;
    private static UUID arts;
    private static UUID complaintOfA;
    private static UUID complaintOfB;

    @BeforeAll
    static void createPeople() throws SQLException {
        String run = UUID.randomUUID().toString().substring(0, 8);
        try (Connection db = TestDatabase.connectAsSuperuser()) {
            admin = queryUuid(db, "select id from users where email = ?", TestDatabase.ADMIN_EMAIL);
            science = queryUuid(db, "insert into departments (name, code) values ('Science', ?) returning id", "SCI-" + run);
            arts = queryUuid(db, "insert into departments (name, code) values ('Arts', ?) returning id", "ART-" + run);
            studentA = createUser(db, "a-" + run + "@test.local", "student", science);
            studentB = createUser(db, "b-" + run + "@test.local", "student", arts);
            teacher = createUser(db, "t-" + run + "@test.local", "teacher", science);
        }
        complaintOfA = fileComplaint(studentA, "A needs help");
        complaintOfB = fileComplaint(studentB, "B needs help");
    }

    @Test
    void studentSeesOnlyTheirOwnComplaints() throws SQLException {
        assertThat(visibleComplaints(studentA)).contains(complaintOfA).doesNotContain(complaintOfB);
        assertThat(visibleComplaints(studentB)).contains(complaintOfB).doesNotContain(complaintOfA);
    }

    @Test
    void studentCannotFileAComplaintForSomeoneElse() {
        assertThatThrownBy(() -> asUser(studentA, db -> {
            try (PreparedStatement ps = db.prepareStatement(
                    "insert into complaints (title, description, student_id) values ('x', 'y', ?)")) {
                ps.setObject(1, studentB);
                ps.executeUpdate();
            }
            return null;
        })).isInstanceOf(SQLException.class)
                .extracting(e -> ((SQLException) e).getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
    }

    @Test
    void reporterIsAlwaysTheSignedInUser() throws SQLException {
        UUID raisedBy = asUser(studentA, db -> {
            try (PreparedStatement ps = db.prepareStatement(
                    "select raised_by from complaints where id = ?")) {
                ps.setObject(1, complaintOfA);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getObject(1, UUID.class);
                }
            }
        });
        assertThat(raisedBy).isEqualTo(studentA);
    }

    @Test
    void teacherSeesComplaintsOfTheirDepartmentOnly() throws SQLException {
        assertThat(visibleComplaints(teacher)).contains(complaintOfA).doesNotContain(complaintOfB);
    }

    @Test
    void superuserSeesEveryComplaint() throws SQLException {
        assertThat(visibleComplaints(admin)).contains(complaintOfA, complaintOfB);
    }

    @Test
    void anonymousSessionSeesNothing() throws SQLException {
        try (Connection db = TestDatabase.connect(TestDatabase.APP_USER, TestDatabase.APP_PASSWORD);
             Statement st = db.createStatement()) {
            assertThat(count(st, "select count(*) from users")).isZero();
            assertThat(count(st, "select count(*) from complaints")).isZero();
        }
    }

    @Test
    void disabledUserLosesAccess() throws SQLException {
        String run = UUID.randomUUID().toString().substring(0, 8);
        UUID disabled;
        try (Connection db = TestDatabase.connectAsSuperuser()) {
            disabled = createUser(db, "d-" + run + "@test.local", "student", science);
        }
        UUID complaint = fileComplaint(disabled, "before disable");
        try (Connection db = TestDatabase.connectAsSuperuser();
             PreparedStatement ps = db.prepareStatement("update users set enabled = false where id = ?")) {
            ps.setObject(1, disabled);
            ps.executeUpdate();
        }
        assertThat(visibleComplaints(disabled)).doesNotContain(complaint);
    }

    @Test
    void authenticatorCannotReadApplicationData() throws SQLException {
        try (Connection db = TestDatabase.connect(TestDatabase.AUTHENTICATOR_USER, TestDatabase.AUTHENTICATOR_PASSWORD);
             Statement st = db.createStatement()) {
            assertThat(count(st, "select count(*) from users where email = '" + TestDatabase.ADMIN_EMAIL + "'"))
                    .isEqualTo(1);
            assertThatThrownBy(() -> st.executeQuery("select count(*) from complaints"))
                    .isInstanceOf(SQLException.class)
                    .extracting(e -> ((SQLException) e).getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    @Test
    void applicationRoleCannotHashPasswordsItself() throws SQLException {
        try (Connection db = TestDatabase.connect(TestDatabase.APP_USER, TestDatabase.APP_PASSWORD);
             Statement st = db.createStatement()) {
            assertThatThrownBy(() -> st.executeQuery("select crypt('x', 'y')"))
                    .isInstanceOf(SQLException.class)
                    .extracting(e -> ((SQLException) e).getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    @Test
    void applicationRolesDoNotBypassRowLevelSecurity() throws SQLException {
        try (Connection db = TestDatabase.connectAsSuperuser(); Statement st = db.createStatement()) {
            assertThat(count(st, "select count(*) from pg_roles where rolname in ('authenticator', 'proctor')"
                    + " and (rolbypassrls or rolsuper)")).isZero();
            assertThat(count(st, "select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace"
                    + " where n.nspname = 'public' and c.relkind = 'r'"
                    + " and c.relname in ('users', 'complaints', 'complaint_comments', 'complaint_files', 'roles')"
                    + " and not c.relrowsecurity")).isZero();
        }
    }

    private static UUID createUser(Connection db, String email, String role, UUID department) throws SQLException {
        UUID id = queryUuid(db, "insert into users (email, name, password, enabled, verified)"
                + " values (?, 'Test User', crypt('pw', gen_salt('bf', 4)), true, true) returning id", email);
        try (PreparedStatement ps = db.prepareStatement(
                "insert into user_roles (user_id, role_id) select ?, id from roles where name = ?")) {
            ps.setObject(1, id);
            ps.setString(2, role);
            assertThat(ps.executeUpdate()).isEqualTo(1);
        }
        try (PreparedStatement ps = db.prepareStatement(
                "insert into department_users (user_id, department_id) values (?, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, department);
            ps.executeUpdate();
        }
        return id;
    }

    private static UUID fileComplaint(UUID student, String title) throws SQLException {
        return asUser(student, db -> {
            try (PreparedStatement ps = db.prepareStatement(
                    "insert into complaints (title, description) values (?, 'details') returning id")) {
                ps.setString(1, title);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getObject(1, UUID.class);
                }
            }
        });
    }

    private static java.util.List<UUID> visibleComplaints(UUID user) throws SQLException {
        return asUser(user, db -> {
            java.util.List<UUID> ids = new java.util.ArrayList<>();
            try (Statement st = db.createStatement(); ResultSet rs = st.executeQuery("select id from complaints")) {
                while (rs.next()) {
                    ids.add(rs.getObject(1, UUID.class));
                }
            }
            return ids;
        });
    }

    private static <T> T asUser(UUID user, SqlWork<T> work) throws SQLException {
        try (Connection db = TestDatabase.connect(TestDatabase.APP_USER, TestDatabase.APP_PASSWORD)) {
            db.setAutoCommit(false);
            try (PreparedStatement ps = db.prepareStatement("select set_config('auth.uid', ?, true)")) {
                ps.setString(1, user.toString());
                ps.executeQuery().close();
            }
            try {
                T result = work.run(db);
                db.commit();
                return result;
            } catch (SQLException e) {
                db.rollback();
                throw e;
            }
        }
    }

    private static UUID queryUuid(Connection db, String sql, String arg) throws SQLException {
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            ps.setString(1, arg);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private static long count(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection db) throws SQLException;
    }
}
