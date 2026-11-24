package org.carebridge.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Identity schema constraints, the role matrix, JdbcIdentityStore and the demo seed import. */
@Testcontainers(disabledWithoutDocker = true)
class IdentityStoreIT {

    private static final Path SEED = Path.of("../../packages/synthetic-data/seed/demo-seed.json");
    private static final String DEMO_PASSWORD = "local-demo-password";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.0-pg17").asCompatibleSubstituteFor("postgres"));

    private static DriverManagerDataSource dataSource;
    private static JdbcClient jdbc;
    private static final PasswordEncoder ENCODER = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private JdbcIdentityStore store;
    private DemoSeedImporter importer;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = JdbcClient.create(dataSource);
    }

    @BeforeEach
    void setUp() {
        store = new JdbcIdentityStore(jdbc);
        importer = new DemoSeedImporter(jdbc, ENCODER,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    private UUID insertUser(String handle, String role) {
        return jdbc.sql("""
                        INSERT INTO users (handle, email, display_name, role)
                        VALUES (:handle, :email, 'Test (DEMO)', :role)
                        RETURNING id
                        """)
                .param("handle", handle)
                .param("email", handle + "@demo.carebridge.invalid")
                .param("role", role)
                .query(UUID.class)
                .single();
    }

    private UUID insertUnit(String code) {
        return jdbc.sql("INSERT INTO care_units (code, name) VALUES (:code, 'Test unit (DEMO)') RETURNING id")
                .param("code", code)
                .query(UUID.class)
                .single();
    }

    private int addMembership(UUID userId, UUID unitId, String role) {
        return jdbc.sql("INSERT INTO team_memberships (user_id, care_unit_id, role) VALUES (:u, :c, :r)")
                .param("u", userId)
                .param("c", unitId)
                .param("r", role)
                .update();
    }

    @Test
    void roleMatrixIsSeededByTheMigration() {
        assertThat(store.roleMatrix()).hasSize(15)
                .contains(new RolePermission(Role.REVIEWER, "proposal.review", RolePermission.Scope.UNIT))
                .contains(new RolePermission(Role.PATIENT, "plan.read_published", RolePermission.Scope.OWN_CASE))
                .noneMatch(row -> row.role() == Role.ADMIN && row.permission().startsWith("case."))
                .noneMatch(row -> row.role() == Role.COORDINATOR && row.permission().equals("proposal.review"));
    }

    @Test
    void membershipRoleMustMatchTheUsersRole() {
        UUID unit = insertUnit("TEST-UNIT-ROLE");
        UUID coordinator = insertUser("role.mismatch", "coordinator");

        assertThatThrownBy(() -> addMembership(coordinator, unit, "reviewer"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("team_memberships_user_role_fkey");
    }

    @Test
    void portalAndAdminAccountsCannotJoinUnits() {
        UUID unit = insertUnit("TEST-UNIT-PORTAL");
        UUID patient = insertUser("portal.member", "patient");

        assertThatThrownBy(() -> addMembership(patient, unit, "patient"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("team_memberships_role_check");
    }

    @Test
    void aMembersRoleCannotChangeWhileTheMembershipExists() {
        UUID unit = insertUnit("TEST-UNIT-SWITCH");
        UUID user = insertUser("role.switch", "coordinator");
        addMembership(user, unit, "coordinator");

        assertThatThrownBy(() -> jdbc.sql("UPDATE users SET role = 'admin' WHERE id = :id").param("id", user).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void handlesAreUniqueIgnoringCase() {
        insertUser("same.handle", "reviewer");

        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO users (handle, email, display_name, role)
                        VALUES ('same.handle', 'other@demo.carebridge.invalid', 'Other (DEMO)', 'reviewer')
                        """).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("users_handle_lower_key");
        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO users (handle, email, display_name, role)
                        VALUES ('x.user', 'SAME.HANDLE@demo.carebridge.invalid', 'Other (DEMO)', 'reviewer')
                        """).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("users_email_lower_key");
    }

    @Test
    void storeReturnsActiveMembershipsOnly() {
        UUID active = insertUnit("TEST-UNIT-ACTIVE");
        UUID inactive = insertUnit("TEST-UNIT-INACTIVE");
        UUID user = insertUser("two.units", "reviewer");
        addMembership(user, active, "reviewer");
        addMembership(user, inactive, "reviewer");
        jdbc.sql("UPDATE team_memberships SET active = false WHERE care_unit_id = :c").param("c", inactive).update();

        Account account = store.findByHandle("TWO.UNITS").orElseThrow();

        assertThat(account.memberships()).extracting(UnitMembership::unitCode).containsExactly("TEST-UNIT-ACTIVE");
        assertThat(account.permissions()).contains("case.read", "proposal.review").doesNotContain("case.create");
        assertThat(account.canSignIn()).isFalse(); // no password hash
        assertThat(store.findById(account.id())).contains(account);
    }

    @Test
    void demoSeedLoadsEveryAccountAndIsIdempotent() throws Exception {
        DemoSeedImporter.Result first = importer.importFile(SEED, DEMO_PASSWORD);
        DemoSeedImporter.Result second = importer.importFile(SEED, DEMO_PASSWORD);

        assertThat(first).isEqualTo(new DemoSeedImporter.Result(3, 8, 6));
        assertThat(second).isEqualTo(first);
        assertThat(jdbc.sql("SELECT count(*) FROM users WHERE external_ref LIKE 'DEMO-USR-%'")
                .query(Integer.class).single()).isEqualTo(8);

        Account omar = store.findByHandle("omar.coordinator").orElseThrow();
        assertThat(omar.memberships()).extracting(UnitMembership::unitCode)
                .containsExactly("DEMO-UNIT-02", "DEMO-UNIT-03");
        assertThat(ENCODER.matches(DEMO_PASSWORD, omar.passwordHash())).isTrue();
        assertThat(omar.passwordHash()).startsWith("{bcrypt}");

        assertThat(store.findByHandle("lee.admin").orElseThrow().memberships()).isEmpty();
        assertThat(store.findByHandle("alvarez.patient").orElseThrow().role()).isEqualTo(Role.PATIENT);
    }

    @Test
    void demoSeedRequiresAPasswordFromTheEnvironment() {
        assertThatThrownBy(() -> importer.importFile(SEED, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CAREBRIDGE_DEMO_PASSWORD");
    }

    @Test
    void nonSyntheticSeedFilesAreRefused(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("seed.json");
        Files.writeString(file, "{\"dataMode\": \"REAL\", \"careUnits\": [], \"users\": []}");

        assertThatThrownBy(() -> importer.importFile(file, DEMO_PASSWORD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SYNTHETIC_ONLY");
    }
}
